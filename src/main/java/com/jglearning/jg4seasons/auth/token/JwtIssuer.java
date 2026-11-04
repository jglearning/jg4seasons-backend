package com.jglearning.jg4seasons.auth.token;

import com.jglearning.jg4seasons.auth.UserRole;
import java.time.Clock;
import java.time.Instant;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/** access JWT 발급. claims: sub(user id), role(소문자), aca(학원 id, 없으면 생략), iat, exp(30분). */
@Component
public class JwtIssuer {

    public static final String ROLE = "role";
    public static final String ACADEMY = "aca";

    private final JwtEncoder encoder;
    private final Clock clock;

    public JwtIssuer(JwtEncoder encoder, Clock clock) {
        this.encoder = encoder;
        this.clock = clock;
    }

    public String issueAccess(long userId, UserRole role, Long academyId) {
        Instant now = clock.instant();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .subject(Long.toString(userId))
                .claim(ROLE, role.value())
                .issuedAt(now)
                .expiresAt(now.plus(SessionTtl.ACCESS));
        if (academyId != null) {
            claims.claim(ACADEMY, academyId);
        }
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }
}
