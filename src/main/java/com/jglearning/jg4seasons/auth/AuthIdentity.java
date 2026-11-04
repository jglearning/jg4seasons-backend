package com.jglearning.jg4seasons.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 사용자의 인증 수단(ADR-05). 개인 코드는 identifier = HMAC 값(PersonalCodeHasher), 평문 없음.
 *
 * <p>academy_id 칸이 없지만 HMAC에 학원 id가 들어가 값 자체가 학원별이다. 찾은 user_id는 반드시 학원 범위(UserRepository)로 다시 읽는다.
 * 실패 횟수·잠금 칸(failed_attempts·locked_until)은 교사·관리자 비밀번호용 — 학생 개인 코드 잠금은 인메모리(ADR-22).
 */
@Entity
@Table(name = "auth_identity")
public class AuthIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Convert(converter = AuthProvider.DbConverter.class)
    @Column(nullable = false, columnDefinition = "enum('personal_code','password','kakao')")
    private AuthProvider provider;

    private String identifier;

    /** password: BCrypt 문자열. personal_code·kakao: null */
    @Column(name = "secret_hash")
    private String secretHash;

    /** 비밀번호 연속 실패 수(교사·관리자). 학생 개인 코드 잠금은 인메모리(LoginAttemptLimiter) */
    @Column(name = "failed_attempts", nullable = false, columnDefinition = "tinyint")
    private int failedAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "issued_at")
    private Instant issuedAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    private static final Pattern HMAC_HEX = Pattern.compile("^[0-9a-f]{64}$");
    private static final Pattern BCRYPT = Pattern.compile("^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$");

    private static final Pattern NORMALIZED_LOGIN_ID = Pattern.compile("^[a-z0-9._-]{3,30}$");

    static final int MAX_WRONG_PASSWORDS = 5;
    static final Duration PASSWORD_LOCK = Duration.ofMinutes(10);

    protected AuthIdentity() {}

    /** @param hashedCode PersonalCodeHasher가 만든 소문자 hex 64자. 평문 개인 코드를 넘기면 IllegalArgumentException(값은 메시지에 넣지 않음) */
    public static AuthIdentity personalCode(long userId, String hashedCode, Instant issuedAt) {
        if (hashedCode == null || !HMAC_HEX.matcher(hashedCode).matches()) {
            throw new IllegalArgumentException("개인 코드 저장값은 HMAC(소문자 hex 64자)이어야 한다");
        }
        AuthIdentity identity = new AuthIdentity();
        identity.userId = userId;
        identity.provider = AuthProvider.PERSONAL_CODE;
        identity.identifier = hashedCode;
        identity.issuedAt = issuedAt;
        return identity;
    }

    /**
     * 교사·관리자 비밀번호 수단. identifier = 로그인 아이디(전체 학원에서 고유, 이미 정리된 값).
     *
     * @param bcryptHash PasswordEncoder가 만든 BCrypt 문자열. 평문을 넘기면 IllegalArgumentException(값은 메시지에 넣지 않음)
     */
    public static AuthIdentity password(long userId, String loginId, String bcryptHash, Instant issuedAt) {
        if (loginId == null || !NORMALIZED_LOGIN_ID.matcher(loginId).matches()) {
            // 로그인은 정리된 소문자로 조회한다 — 정리 안 된 값이 저장되면 그 계정은 영영 로그인되지 않는다
            throw new IllegalArgumentException("로그인 아이디는 LoginIds.normalize를 거친 3~30자 소문자여야 한다");
        }
        requireBcrypt(bcryptHash);
        AuthIdentity identity = new AuthIdentity();
        identity.userId = userId;
        identity.provider = AuthProvider.PASSWORD;
        identity.identifier = loginId;
        identity.secretHash = bcryptHash;
        identity.issuedAt = issuedAt;
        return identity;
    }

    public void markUsed(Instant at) {
        this.lastUsedAt = at;
    }

    public boolean lockedAt(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    /** 지금까지 연속으로 틀린 횟수(로그용) */
    public int failedAttempts() {
        return failedAttempts;
    }

    public Instant lockedUntil() {
        return lockedUntil;
    }

    /**
     * 비밀번호 틀림 1회. 5회째면 잠그고 해제 시각을 돌려준다. 잠금이 끝난 뒤 첫 실패는 1부터 센다. 해제 시각은 초를 올려 저장한다 — DB DATETIME이
     * 소수 초를 반올림해 잠금이 앞당겨지지 않게. 호출자는 이 행을 잠그고(findPasswordIdentityForUpdate) 부른다 — 동시 실패로 5회를 넘지 못하게
     */
    public Optional<Instant> recordWrongPassword(Instant now) {
        if (lockedAt(now)) {
            return Optional.of(lockedUntil); // 잠긴 동안은 세지도 늘리지도 않는다(호출 순서에 기대지 않게)
        }
        if (lockedUntil != null) {
            failedAttempts = 0;
            lockedUntil = null;
        }
        failedAttempts++;
        if (failedAttempts >= MAX_WRONG_PASSWORDS) {
            lockedUntil = ceilToSecond(now).plus(PASSWORD_LOCK);
            return Optional.of(lockedUntil);
        }
        return Optional.empty();
    }

    /** 비밀번호 맞음: 실패 0, 잠금 해제. 계정 단위라 남의 계정 횟수를 지울 수 없다 */
    public void recordSuccess(Instant now) {
        failedAttempts = 0;
        lockedUntil = null;
        lastUsedAt = now;
    }

    /** 관리자가 비밀번호를 재설정: 실패 0, 잠금 해제 */
    public void changePassword(String bcryptHash, Instant now) {
        requireBcrypt(bcryptHash);
        secretHash = bcryptHash;
        failedAttempts = 0;
        lockedUntil = null;
        issuedAt = now;
    }

    /** 비밀번호 비교용 BCrypt 문자열 */
    public String secretHash() {
        return secretHash;
    }

    public String loginId() {
        return provider == AuthProvider.PASSWORD ? identifier : null;
    }

    private static void requireBcrypt(String hash) {
        if (hash == null || !BCRYPT.matcher(hash).matches()) {
            throw new IllegalArgumentException("비밀번호 저장값은 BCrypt 해시여야 한다");
        }
    }

    private static Instant ceilToSecond(Instant instant) {
        Instant floor = instant.truncatedTo(ChronoUnit.SECONDS);
        return floor.equals(instant) ? floor : floor.plusSeconds(1);
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public AuthProvider getProvider() {
        return provider;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }
}
