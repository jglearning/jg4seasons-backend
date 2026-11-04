package com.jglearning.jg4seasons.auth;

import com.jglearning.jg4seasons.auth.ratelimit.CodeGuessLimiter;
import com.jglearning.jg4seasons.auth.token.IssuedTokens;
import com.jglearning.jg4seasons.auth.token.JwtIssuer;
import com.jglearning.jg4seasons.auth.token.RandomTokens;
import com.jglearning.jg4seasons.auth.token.RefreshTokenService;
import com.jglearning.jg4seasons.auth.token.SessionTtl;
import com.jglearning.jg4seasons.auth.web.dto.StudentLoginResponse;
import com.jglearning.jg4seasons.clazz.api.ClassDirectory;
import com.jglearning.jg4seasons.clazz.api.ClassEntry;
import com.jglearning.jg4seasons.infra.web.error.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 1.2 가입 코드 + 개인 코드로 학생 입장(ADR-22).
 *
 * <p>순서: 가입 코드 → 활성 반(정지 학원 제외). 없으면 가입 코드 찍기 한도(IP당 1분 10회)를 쓰고 401 · (반, IP) 시도를 먼저 센다(잠겼으면 423) · HMAC으로 인증 수단
 * → 사용자는 반의 학원 범위로 다시 읽음(활성 학생) → 그 반 현재 소속. 어느 단계든 틀리면 반 전체 실패 1회, 5번째 시도였으면 423. 맞으면 센 시도 1회만 되돌린다. 실패 응답은 어느 쪽이
 * 틀렸는지 구분하지 않는다. 개인 코드 평문은 해시 계산에만 쓰고 남기지 않는다.
 */
@Service
@Transactional
public class StudentLoginService {

    public record LoginResult(StudentLoginResponse body, IssuedTokens tokens) {}

    private static final String WRONG = "가입 코드와 개인 코드를 다시 확인해 주세요";
    private static final String LOCKED = "잠시 쉬었다가 다시 시도해 주세요";

    private final ClassDirectory classes;
    private final EnterableClasses enterableClasses;
    private final CodeGuessLimiter codeGuessLimiter;
    private final LoginAttemptLimiter attempts;
    private final PersonalCodeHasher hasher;
    private final AuthIdentityRepository identities;
    private final UserRepository users;
    private final JwtIssuer jwtIssuer;
    private final RefreshTokenService refreshTokens;
    private final Clock clock;

    StudentLoginService(
            ClassDirectory classes,
            EnterableClasses enterableClasses,
            CodeGuessLimiter codeGuessLimiter,
            LoginAttemptLimiter attempts,
            PersonalCodeHasher hasher,
            AuthIdentityRepository identities,
            UserRepository users,
            JwtIssuer jwtIssuer,
            RefreshTokenService refreshTokens,
            Clock clock) {
        this.classes = classes;
        this.enterableClasses = enterableClasses;
        this.codeGuessLimiter = codeGuessLimiter;
        this.attempts = attempts;
        this.hasher = hasher;
        this.identities = identities;
        this.users = users;
        this.jwtIssuer = jwtIssuer;
        this.refreshTokens = refreshTokens;
        this.clock = clock;
    }

    public LoginResult login(String rawCode, String personalCode, String clientIp, String userAgent) {
        Optional<ClassEntry> found = enterableClasses.find(rawCode);
        if (found.isEmpty()) {
            codeGuessLimiter.acquire(clientIp);
            throw new ApiException(AuthErrorCode.INVALID_CREDENTIALS, WRONG);
        }
        ClassEntry clazz = found.get();
        attempts.beginAttempt(clazz.id(), clientIp).ifPresent(until -> {
            throw locked(until);
        });

        Optional<AuthIdentity> identity = identities.findByProviderAndIdentifier(
                AuthProvider.PERSONAL_CODE, hasher.hash(clazz.academyId(), personalCode));
        Optional<User> student = identity.flatMap(id -> users.findByAcademyIdAndId(clazz.academyId(), id.getUserId()))
                .filter(User::isActive)
                .filter(user -> user.getRole() == UserRole.STUDENT)
                .filter(user -> classes.isCurrentMember(clazz.academyId(), clazz.id(), user.getId()));
        if (student.isEmpty()) {
            Optional<Instant> lockedNow = attempts.recordFailure(clazz.id(), clientIp);
            if (lockedNow.isPresent()) {
                throw locked(lockedNow.get());
            }
            throw new ApiException(AuthErrorCode.INVALID_CREDENTIALS, WRONG);
        }

        attempts.recordSuccess(clazz.id(), clientIp);
        User user = student.get();
        Instant now = clock.instant();
        user.recordLogin(now);
        identity.get().markUsed(now);
        IssuedTokens tokens = new IssuedTokens(
                jwtIssuer.issueAccess(user.getId(), user.getRole().value(), clazz.academyId()),
                refreshTokens.create(user.getId(), userAgent, SessionTtl.STUDENT_REFRESH),
                RandomTokens.next(),
                SessionTtl.STUDENT_REFRESH);
        StudentLoginResponse body = new StudentLoginResponse(
                new StudentLoginResponse.UserInfo(
                        user.getId(), user.getName(), user.getRole().value()),
                new StudentLoginResponse.ClassRef(clazz.id(), clazz.name()));
        return new LoginResult(body, tokens);
    }

    private static ApiException locked(Instant until) {
        return new ApiException(AuthErrorCode.LOCKED, LOCKED).with("until", until);
    }
}
