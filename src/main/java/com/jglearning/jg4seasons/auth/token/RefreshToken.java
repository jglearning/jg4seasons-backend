package com.jglearning.jg4seasons.auth.token;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * refresh 토큰. token_hash = 쿠키 값의 SHA-256 hex(평문 없음).
 *
 * <p>폐기는 두 가지다. 회전: revoked_at만 기록 — 유예 안에 다시 오면 GraceReuse. 끊기(로그아웃·재사용 감지): revoked_at과 expires_at을 함께
 * 지금으로 — 만료로 먼저 걸려 유예 없이 거부. 폐기 사유 칸이 없어 이렇게 구분한다. DB DATETIME은 초 단위(반올림)라 저장하는 시각은 초를 내린다.
 */
@Entity
@Table(name = "refresh_token")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, columnDefinition = "char(64)")
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "user_agent")
    private String userAgent;

    protected RefreshToken() {}

    RefreshToken(long userId, String tokenHash, Instant expiresAt, String userAgent) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.userAgent = userAgent;
    }

    boolean isExpiredAt(Instant now) {
        return !expiresAt.isAfter(now);
    }

    boolean isRevoked() {
        return revokedAt != null;
    }

    Instant revokedAt() {
        return revokedAt;
    }

    long userId() {
        return userId;
    }

    String userAgent() {
        return userAgent;
    }

    /** 회전: 유예 판정을 위해 폐기 시각만(초 내림) */
    void markRotated(Instant now) {
        this.revokedAt = floorToSecond(now);
    }

    /** 끊기: 유예 없이 바로 거부되게 만료도 지금으로(초 내림) */
    void kill(Instant now) {
        if (revokedAt == null) {
            this.revokedAt = floorToSecond(now);
        }
        this.expiresAt = floorToSecond(now);
    }

    /**
     * DB DATETIME은 소수 초를 반올림한다. 12:00:00.600에 끊으면 12:00:01로 저장돼 0.4초 동안 "만료 전"으로 보인다. 저장 전에 초를 내려 저장값이 항상 지금
     * 이하가 되게 한다.
     */
    static Instant floorToSecond(Instant instant) {
        return instant.truncatedTo(ChronoUnit.SECONDS);
    }
}
