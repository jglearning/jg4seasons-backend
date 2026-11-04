package com.jglearning.jg4seasons.auth.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jglearning.jg4seasons.MutableClock;
import com.jglearning.jg4seasons.auth.UserRole;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

/** access JWT: HS256, claims sub·role·aca, 30분, 시계 오차 허용 없음. */
class JwtIssuerTest {

    static final String SECRET = "test-jwt-secret-0123456789abcdef0123";

    final MutableClock clock = new MutableClock(Instant.parse("2026-11-05T00:00:00Z"));
    final JwtIssuer issuer = new JwtIssuer(JwtConfig.encoder(SECRET), clock);
    final JwtDecoder decoder = JwtConfig.decoder(SECRET, clock);

    @Test
    void 발급한_access는_sub_role_aca를_담는다() {
        Jwt jwt = decoder.decode(issuer.issueAccess(301L, UserRole.STUDENT, 12L));

        assertThat(jwt.getSubject()).isEqualTo("301");
        assertThat(jwt.getClaimAsString("role")).isEqualTo("student");
        assertThat(((Number) jwt.getClaim("aca")).longValue()).isEqualTo(12L);
        assertThat(jwt.getIssuedAt()).isEqualTo(clock.instant());
        assertThat(jwt.getExpiresAt()).isEqualTo(clock.instant().plus(Duration.ofMinutes(30)));
        assertThat(jwt.getHeaders().get("alg")).hasToString("HS256");
    }

    @Test
    void 학원이_없는_사용자는_aca가_없다() {
        Jwt jwt = decoder.decode(issuer.issueAccess(1L, UserRole.SUPERADMIN, null));

        assertThat(jwt.hasClaim("aca")).isFalse();
    }

    @Test
    void 만료_직전에는_통과하고_30분이_지나면_실패한다() {
        String token = issuer.issueAccess(301L, UserRole.STUDENT, 12L);

        clock.advance(Duration.ofMinutes(30).minusSeconds(1));
        assertThat(decoder.decode(token).getSubject()).isEqualTo("301");

        clock.advance(Duration.ofSeconds(2)); // 30분 + 1초(만료 시각 정각은 통과 — Spring 검증기는 exp 이후만 거부)
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void 다른_키로_서명한_토큰은_거부한다() {
        String forged = new JwtIssuer(JwtConfig.encoder("other-secret-0123456789abcdef012345"), clock)
                .issueAccess(301L, UserRole.STUDENT, 12L);

        assertThatThrownBy(() -> decoder.decode(forged)).isInstanceOf(JwtException.class);
    }
}
