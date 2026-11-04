package com.jglearning.jg4seasons.auth.token;

import com.jglearning.jg4seasons.auth.AuthProperties;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * access JWT 서명·검증(HS256, 키는 jg4seasons.auth.jwt-secret).
 *
 * <p>검증은 만료(exp)만 본다. 시계 오차 허용은 0이고 시계는 주입 Clock이다 — 서버 하나가 발급·검증을 함께 해서 오차가 없다. 기본 검증기(60초 허용)를 쓰지
 * 않는다.
 */
@Configuration(proxyBeanMethods = false)
public class JwtConfig {

    @Bean
    public JwtEncoder jwtEncoder(AuthProperties properties) {
        return encoder(properties.jwtSecret());
    }

    @Bean
    public JwtDecoder jwtDecoder(AuthProperties properties, Clock clock) {
        return decoder(properties.jwtSecret(), clock);
    }

    static JwtEncoder encoder(String secret) {
        return NimbusJwtEncoder.withSecretKey(key(secret))
                .algorithm(MacAlgorithm.HS256)
                .build();
    }

    static JwtDecoder decoder(String secret, Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key(secret))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        JwtTimestampValidator expiry = new JwtTimestampValidator(Duration.ZERO);
        expiry.setClock(clock);
        decoder.setJwtValidator(expiry);
        return decoder;
    }

    private static SecretKey key(String secret) {
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
}
