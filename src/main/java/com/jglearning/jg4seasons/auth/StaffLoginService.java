package com.jglearning.jg4seasons.auth;

import com.jglearning.jg4seasons.academy.api.AcademyDirectory;
import com.jglearning.jg4seasons.auth.persistence.AuthUser;
import com.jglearning.jg4seasons.auth.persistence.PreAuthUserLookup;
import com.jglearning.jg4seasons.auth.ratelimit.StaffLoginGuessLimiter;
import com.jglearning.jg4seasons.auth.token.IssuedTokens;
import com.jglearning.jg4seasons.auth.token.TokenIssuer;
import com.jglearning.jg4seasons.auth.web.dto.StaffLoginResponse;
import com.jglearning.jg4seasons.infra.web.error.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 1.4 교사·관리자 로그인(아이디 + 비밀번호).
 *
 * <p>순서: IP 한도(모든 시도, 1분 10회) → 아이디 정리 → 비밀번호 수단을 행 잠금으로 읽음(같은 계정 동시 실패가 5회를 넘지 못하게). 없으면 더미 해시와 비교(응답 시간을 맞춰 아이디 존재를
 * 드러내지 않음) → 401 · 사용자(활성, teacher·admin이면 학원 있고 정지 아님, 또는 학원 없는 운영자 — ADR 0032) · 잠겼으면 423 · 비밀번호 틀림 → 실패 1회(5번째면 423) · 맞으면
 * 실패 0, 토큰 발급.
 *
 * <p>틀린 비밀번호의 실패 횟수는 401·423을 던져도 남아야 한다 → ApiException에는 롤백하지 않는다(이 서비스가 쓰는 것은 실패 횟수·잠금·로그인 시각뿐).
 */
@Service
@Transactional(noRollbackFor = ApiException.class)
public class StaffLoginService {

    public record LoginResult(StaffLoginResponse body, IssuedTokens tokens) {}

    private static final Logger log = LoggerFactory.getLogger(StaffLoginService.class);
    private static final String WRONG = "아이디 또는 비밀번호가 맞지 않아요";
    private static final String LOCKED = "잠시 쉬었다가 다시 시도해 주세요";
    private static final Set<UserRole> STAFF = Set.of(UserRole.TEACHER, UserRole.ADMIN);

    private final AuthIdentityRepository identities;
    private final PreAuthUserLookup preAuthUsers;
    private final UserRepository users;
    private final AcademyDirectory academies;
    private final PasswordEncoder passwordEncoder;
    private final StaffLoginGuessLimiter guessLimiter;
    private final TokenIssuer tokenIssuer;
    private final Clock clock;

    StaffLoginService(
            AuthIdentityRepository identities,
            PreAuthUserLookup preAuthUsers,
            UserRepository users,
            AcademyDirectory academies,
            PasswordEncoder passwordEncoder,
            StaffLoginGuessLimiter guessLimiter,
            TokenIssuer tokenIssuer,
            Clock clock) {
        this.identities = identities;
        this.preAuthUsers = preAuthUsers;
        this.users = users;
        this.academies = academies;
        this.passwordEncoder = passwordEncoder;
        this.guessLimiter = guessLimiter;
        this.tokenIssuer = tokenIssuer;
        this.clock = clock;
    }

    public LoginResult login(String rawLoginId, String password, String clientIp, String userAgent) {
        // 모든 시도를 확인 전에 센다: 있는 계정마다 5회 미만으로 나눠 찍는 대입(password spraying)도 막고,
        // 429가 계정 존재 여부와 무관하게 나오게(아이디 존재를 드러내지 않음)
        guessLimiter.acquire(clientIp);
        Optional<AuthIdentity> found = identities.findPasswordIdentityForUpdate(LoginIds.normalize(rawLoginId));
        if (found.isEmpty()) {
            passwordEncoder.matches(password, PasswordConfig.DUMMY_HASH);
            throw wrong();
        }
        AuthIdentity identity = found.get();
        Instant now = clock.instant();
        Optional<AuthUser> staff = preAuthUsers
                .findActiveUser(identity.getUserId())
                .filter(user -> user.isOperator()
                        || (STAFF.contains(user.role())
                                && user.academyId() != null
                                && academies.isActive(user.academyId())));
        if (identity.lockedAt(now)) {
            throw locked(identity.lockedUntil());
        }
        boolean matches = passwordEncoder.matches(password, identity.secretHash());
        if (staff.isEmpty()) {
            // 응답은 다른 실패와 같은 401이지만, 문의에 답할 수 있게 사용자만 남긴다(입력값 없음)
            log.info("staff login rejected: not eligible userId={}", identity.getUserId());
            throw wrong();
        }
        if (!matches) {
            Optional<Instant> lockedNow = identity.recordWrongPassword(now);
            if (lockedNow.isPresent()) {
                // 교사 문의에 답할 수 있게 사용자와 해제 시각만(비밀번호·IP 없음)
                log.info("staff login locked userId={} until={}", identity.getUserId(), lockedNow.get());
                throw locked(lockedNow.get());
            }
            log.info(
                    "staff login wrong password userId={} attempts={}",
                    identity.getUserId(),
                    identity.failedAttempts());
            throw wrong();
        }

        AuthUser user = staff.get();
        identity.recordSuccess(now);
        if (user.isOperator()) {
            // 운영자는 학원 범위 리포지토리로 찾을 수 없다(ADR 0032)
            preAuthUsers.recordOperatorLogin(user.id(), now);
        } else {
            // 바로 위에서 같은 id의 활성 사용자를 읽었으니 반드시 있다 — 없으면 조용히 넘어가지 않고 드러나게
            users.findByAcademyIdAndId(user.academyId(), user.id())
                    .orElseThrow(() -> new IllegalStateException("staff user vanished userId=" + user.id()))
                    .recordLogin(now);
        }
        IssuedTokens tokens = tokenIssuer.issue(user.id(), user.role(), user.academyId(), userAgent);
        return new LoginResult(
                new StaffLoginResponse(new StaffLoginResponse.UserInfo(
                        user.id(), user.name(), user.role().value(), user.academyId())),
                tokens);
    }

    private static ApiException wrong() {
        return new ApiException(AuthErrorCode.INVALID_CREDENTIALS, WRONG);
    }

    private static ApiException locked(Instant until) {
        return new ApiException(AuthErrorCode.LOCKED, LOCKED).with("until", until);
    }
}
