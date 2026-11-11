package com.jglearning.jg4seasons.auth.token;

import com.jglearning.jg4seasons.academy.api.AcademyDirectory;
import com.jglearning.jg4seasons.auth.AuthProperties;
import com.jglearning.jg4seasons.auth.UserRole;
import com.jglearning.jg4seasons.auth.persistence.AuthUser;
import com.jglearning.jg4seasons.auth.persistence.PreAuthUserLookup;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * refresh 저장·회전·끊기.
 *
 * <p>회전 판정(이 순서): 없음·만료 → Rejected · 폐기됐고 폐기 뒤 유예(기본 10초) 안 → GraceReuse · 유예 뒤 → 그 사용자 refresh 전부 끊고
 * Rejected(빼낸 쿠키와 학생 중 한쪽이 옛 토큰을 쓴 것) · 유효 → 소유자 확인(활성 사용자, 정지 아닌 학원) 뒤 새 토큰. 토큰 행은 잠그고 읽어 같은 토큰의 동시
 * 회전은 하나만 새 토큰을 받는다.
 */
@Service
@Transactional
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    static final int USER_AGENT_MAX = 200;

    private final RefreshTokenRepository tokens;
    private final PreAuthUserLookup users;
    private final AcademyDirectory academies;
    private final Clock clock;
    private final Duration reuseGrace;

    RefreshTokenService(
            RefreshTokenRepository tokens,
            PreAuthUserLookup users,
            AcademyDirectory academies,
            Clock clock,
            AuthProperties properties) {
        this.tokens = tokens;
        this.users = users;
        this.academies = academies;
        this.clock = clock;
        this.reuseGrace = properties.refreshReuseGrace();
    }

    /** 테스트용: 학생 수명(90일). 운영 코드는 TokenIssuer가 역할별 수명으로 3인자 create를 부른다 */
    String create(long userId, String userAgent) {
        return create(userId, userAgent, SessionTtl.STUDENT_REFRESH);
    }

    public String create(long userId, String userAgent, Duration ttl) {
        String plain = RandomTokens.next();
        tokens.save(new RefreshToken(
                userId, RandomTokens.sha256Hex(plain), clock.instant().plus(ttl), truncate(userAgent)));
        return plain;
    }

    public RefreshOutcome rotate(String presented) {
        Instant now = clock.instant();
        Optional<RefreshToken> found = tokens.findByTokenHashForUpdate(RandomTokens.sha256Hex(presented));
        if (found.isEmpty() || found.get().isExpiredAt(now)) {
            return new RefreshOutcome.Rejected();
        }
        RefreshToken token = found.get();
        if (token.isRevoked()) {
            if (!now.isAfter(token.revokedAt().plus(reuseGrace))) {
                return activeOwner(token.userId())
                        .<RefreshOutcome>map(RefreshOutcome.GraceReuse::new)
                        .orElseGet(RefreshOutcome.Rejected::new);
            }
            int killed = tokens.killAllOf(token.userId(), RefreshTokenRepository.floor(now));
            // 빼낸 쿠키와 학생 중 한쪽이 옛 토큰을 쓴 것 — 운영에서 "자꾸 로그아웃돼요"의 원인을 찾을 수 있게(토큰 값은 남기지 않음)
            log.warn("refresh reuse after grace userId={} killed={}", token.userId(), killed);
            return new RefreshOutcome.Rejected();
        }
        Optional<AuthUser> owner = activeOwner(token.userId());
        if (owner.isEmpty()) {
            log.info("refresh rejected: owner not active userId={}", token.userId());
            token.kill(now);
            return new RefreshOutcome.Rejected();
        }
        token.markRotated(now);
        Duration ttl = SessionTtl.refreshFor(owner.get().role());
        String next = create(token.userId(), token.userAgent(), ttl);
        return new RefreshOutcome.Rotated(owner.get(), next, ttl);
    }

    /** 로그아웃: 이 토큰을 유예 없이 끊는다. 없거나 이미 끊긴 토큰은 무시 */
    public void revoke(String presented) {
        tokens.findByTokenHashForUpdate(RandomTokens.sha256Hex(presented)).ifPresent(t -> t.kill(clock.instant()));
    }

    /** 그 사용자의 refresh를 모두 끊는다(개인 코드 재발급·퇴원 등) */
    public void killAllOf(long userId) {
        tokens.killAllOf(userId, RefreshTokenRepository.floor(clock.instant()));
    }

    private Optional<AuthUser> activeOwner(long userId) {
        // 운영자(superadmin + 학원 없음) 또는 학원 계정(superadmin 아님 + 학원 정지 아님) — 로그인과 같은 기준(ADR 0032). 둘 다 아닌 행(학원이 붙은
        // superadmin 등 데이터 오류)은 회전 전에 걸러 부수효과 없이 거부
        return users.findActiveUser(userId)
                .filter(owner -> owner.isOperator()
                        || (owner.role() != UserRole.SUPERADMIN
                                && owner.academyId() != null
                                && academies.isActive(owner.academyId())));
    }

    private static String truncate(String userAgent) {
        if (userAgent == null) {
            return null;
        }
        return userAgent.length() <= USER_AGENT_MAX ? userAgent : userAgent.substring(0, USER_AGENT_MAX);
    }
}
