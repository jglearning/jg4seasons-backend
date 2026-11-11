package com.jglearning.jg4seasons.auth.token;

import com.jglearning.jg4seasons.auth.AudioScopes;
import com.jglearning.jg4seasons.auth.UserRole;
import com.jglearning.jg4seasons.auth.persistence.AuthUser;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * 쿠키로 나가는 토큰 묶음(access·refresh·csrf·음원 범위)을 만드는 한 곳. refresh의 DB 만료와 쿠키 수명이 같은 값(역할별 SessionTtl)에서 나온다 — 호출자가
 * 수명을 따로 넘기다 어긋나지 않게.
 */
@Component
public class TokenIssuer {

    private final JwtIssuer jwtIssuer;
    private final RefreshTokenService refreshTokens;
    private final AudioScopes audioScopes;

    TokenIssuer(JwtIssuer jwtIssuer, RefreshTokenService refreshTokens, AudioScopes audioScopes) {
        this.jwtIssuer = jwtIssuer;
        this.refreshTokens = refreshTokens;
        this.audioScopes = audioScopes;
    }

    /** 로그인: 새 refresh를 저장하고 묶음을 만든다. academyId는 운영자면 null(JWT aca 없음) */
    public IssuedTokens issue(long userId, UserRole role, Long academyId, String userAgent) {
        Duration ttl = SessionTtl.refreshFor(role);
        return new IssuedTokens(
                jwtIssuer.issueAccess(userId, role, academyId),
                refreshTokens.create(userId, userAgent, ttl),
                RandomTokens.next(),
                ttl,
                audioScopes.of(userId, role, academyId));
    }

    /** refresh 회전: 새 refresh는 RefreshTokenService.rotate가 이미 저장했다 */
    public IssuedTokens afterRotation(AuthUser owner, String newRefresh, Duration refreshTtl) {
        return new IssuedTokens(
                jwtIssuer.issueAccess(owner.id(), owner.role(), owner.academyId()),
                newRefresh,
                RandomTokens.next(),
                refreshTtl,
                audioScopes.of(owner.id(), owner.role(), owner.academyId()));
    }
}
