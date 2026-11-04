package com.jglearning.jg4seasons.auth;

import com.jglearning.jg4seasons.academy.api.AcademyDirectory;
import com.jglearning.jg4seasons.auth.api.AccountStatus;
import com.jglearning.jg4seasons.auth.api.NewStaff;
import com.jglearning.jg4seasons.auth.api.StaffAccount;
import com.jglearning.jg4seasons.auth.api.StaffAccounts;
import com.jglearning.jg4seasons.auth.api.StaffChange;
import com.jglearning.jg4seasons.auth.api.StaffRole;
import com.jglearning.jg4seasons.auth.token.RefreshTokenService;
import com.jglearning.jg4seasons.infra.web.error.ApiException;
import com.jglearning.jg4seasons.infra.web.error.CommonErrorCode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class StaffAccountService implements StaffAccounts {

    private static final Set<UserRole> STAFF = Set.of(UserRole.TEACHER, UserRole.ADMIN);
    private static final Pattern LOGIN_ID = Pattern.compile("^[a-z0-9._-]{3,30}$");
    static final int MIN_PASSWORD = 8;
    /** BCrypt는 72바이트 뒤를 버린다 — 길게 넣고 앞부분만으로 로그인되는 일이 없게 */
    static final int MAX_PASSWORD_BYTES = 72;

    private final UserRepository users;
    private final AuthIdentityRepository identities;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokens;
    private final AcademyDirectory academies;
    private final Clock clock;

    StaffAccountService(
            UserRepository users,
            AuthIdentityRepository identities,
            PasswordEncoder passwordEncoder,
            RefreshTokenService refreshTokens,
            AcademyDirectory academies,
            Clock clock) {
        this.users = users;
        this.identities = identities;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokens = refreshTokens;
        this.academies = academies;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public void requireActiveAdmin(long academyId, long actorId) {
        boolean activeAdmin = users.findByAcademyIdAndId(academyId, actorId)
                .filter(User::isActive)
                .filter(user -> user.getRole() == UserRole.ADMIN)
                .isPresent();
        if (!activeAdmin || !academies.isActive(academyId)) {
            throw new ApiException(CommonErrorCode.UNAUTHORIZED, "다시 로그인해 주세요");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<StaffAccount> list(long academyId) {
        return users.findByAcademyIdAndRoleInOrderByIdAsc(academyId, STAFF).stream()
                .map(this::toAccount)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public StaffAccount find(long academyId, long staffId) {
        return toAccount(staff(academyId, staffId));
    }

    @Override
    public StaffAccount create(long academyId, NewStaff staff) {
        String loginId = loginId(staff.loginId());
        String hash = hash(staff.password());
        if (identities.existsByProviderAndIdentifier(AuthProvider.PASSWORD, loginId)) {
            throw loginIdTaken();
        }
        Instant now = clock.instant();
        User user = users.save(User.staff(academyId, name(staff.name()), toUserRole(staff.role())));
        try {
            identities.saveAndFlush(AuthIdentity.password(user.getId(), loginId, hash, now));
        } catch (DataIntegrityViolationException e) {
            // 동시에 같은 아이디로 만든 경우(UNIQUE(provider, identifier))
            throw loginIdTaken();
        }
        return toAccount(user);
    }

    @Override
    public StaffAccount change(long academyId, long actorId, long staffId, StaffChange change) {
        User user = staff(academyId, staffId);
        Instant now = clock.instant();
        change.name().ifPresent(name -> user.rename(name(name)));
        boolean endSessions = false;
        if (change.status().isPresent()) {
            AccountStatus status = change.status().get();
            if (status == AccountStatus.INACTIVE && staffId == actorId) {
                throw new ApiException(AuthErrorCode.SELF_CHANGE_NOT_ALLOWED, "자기 계정은 비활성화할 수 없어요");
            }
            user.changeStatus(status == AccountStatus.ACTIVE ? UserStatus.ACTIVE : UserStatus.INACTIVE);
            endSessions = status == AccountStatus.INACTIVE;
        }
        if (change.password().isPresent()) {
            String hash = hash(change.password().get());
            identities
                    .findByUserIdAndProvider(user.getId(), AuthProvider.PASSWORD)
                    .ifPresentOrElse(identity -> identity.changePassword(hash, now), () -> {
                        throw new ApiException(CommonErrorCode.NOT_FOUND, "로그인 수단이 없는 계정이에요");
                    });
            endSessions = true;
        }
        if (endSessions) {
            refreshTokens.killAllOf(user.getId());
        }
        return toAccount(user);
    }

    private User staff(long academyId, long staffId) {
        return users.findByAcademyIdAndId(academyId, staffId)
                .filter(user -> STAFF.contains(user.getRole()))
                .orElseThrow(() -> new ApiException(CommonErrorCode.NOT_FOUND, "교사를 찾을 수 없어요"));
    }

    private StaffAccount toAccount(User user) {
        String loginId = identities
                .findByUserIdAndProvider(user.getId(), AuthProvider.PASSWORD)
                .map(AuthIdentity::loginId)
                .orElse(null);
        return new StaffAccount(
                user.getId(),
                user.getName(),
                loginId,
                toStaffRole(user.getRole()),
                switch (user.getStatus()) {
                    case ACTIVE -> AccountStatus.ACTIVE;
                    case INACTIVE -> AccountStatus.INACTIVE;
                },
                user.getLastLoginAt());
    }

    private static String loginId(String raw) {
        String loginId = raw == null ? "" : LoginIds.normalize(raw);
        if (!LOGIN_ID.matcher(loginId).matches()) {
            throw ApiException.validation("loginId", LoginIds.MESSAGE);
        }
        return loginId;
    }

    private String hash(String password) {
        if (password == null
                || password.length() < MIN_PASSWORD
                || password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw ApiException.validation("password", "비밀번호는 8자 이상, 72바이트 이하예요");
        }
        return passwordEncoder.encode(password);
    }

    private static String name(String name) {
        String trimmed = name == null ? "" : name.strip();
        if (trimmed.isEmpty() || trimmed.length() > 30) {
            throw ApiException.validation("name", "이름은 1~30자예요");
        }
        return trimmed;
    }

    /** 교사·관리자만 여기까지 온다(staff() 필터). 값이 늘면 컴파일러가 짚는다(default 없음) */
    private static StaffRole toStaffRole(UserRole role) {
        return switch (role) {
            case TEACHER -> StaffRole.TEACHER;
            case ADMIN -> StaffRole.ADMIN;
            case STUDENT, SUPERADMIN -> throw new IllegalStateException("교사 계정이 아니다: " + role);
        };
    }

    private static UserRole toUserRole(StaffRole role) {
        return switch (role) {
            case TEACHER -> UserRole.TEACHER;
            case ADMIN -> UserRole.ADMIN;
        };
    }

    private static ApiException loginIdTaken() {
        return new ApiException(AuthErrorCode.LOGIN_ID_TAKEN, "다른 아이디를 써 주세요");
    }
}
