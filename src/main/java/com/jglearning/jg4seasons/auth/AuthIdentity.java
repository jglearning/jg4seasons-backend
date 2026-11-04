package com.jglearning.jg4seasons.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 사용자의 인증 수단(ADR-05). 개인 코드는 identifier = HMAC 값(PersonalCodeHasher), 평문 없음.
 *
 * <p>academy_id 칸이 없지만 HMAC에 학원 id가 들어가 값 자체가 학원별이다. 찾은 user_id는 반드시 학원 범위(UserRepository)로 다시 읽는다.
 * 실패 횟수·잠금 칸(failed_attempts·locked_until)은 교사 비밀번호용이라 매핑하지 않는다 — 학생 잠금은 인메모리(ADR-22).
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

    @Column(name = "issued_at")
    private Instant issuedAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    protected AuthIdentity() {}

    public static AuthIdentity personalCode(long userId, String hashedCode, Instant issuedAt) {
        AuthIdentity identity = new AuthIdentity();
        identity.userId = userId;
        identity.provider = AuthProvider.PERSONAL_CODE;
        identity.identifier = hashedCode;
        identity.issuedAt = issuedAt;
        return identity;
    }

    public void markUsed(Instant at) {
        this.lastUsedAt = at;
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
