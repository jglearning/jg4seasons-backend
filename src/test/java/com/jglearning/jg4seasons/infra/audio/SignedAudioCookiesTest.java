package com.jglearning.jg4seasons.infra.audio;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.MutableClock;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class SignedAudioCookiesTest {

    @Test
    void 만료는_주입된_Clock_더하기_수명() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        CloudFrontCookieSigner signer =
                new CloudFrontCookieSigner("K1", generator.generateKeyPair().getPrivate(), "https://site.test/audio/*");
        MutableClock clock = new MutableClock(Instant.parse("2026-11-10T00:00:00Z"));

        SignedAudioCookies cookies = new SignedAudioCookies(signer, clock, Duration.ofHours(2));

        String policy = new String(
                Base64.getDecoder()
                        .decode(cookies.issue()
                                .get(CloudFrontCookieSigner.POLICY)
                                .replace('-', '+')
                                .replace('_', '=')
                                .replace('~', '/')),
                StandardCharsets.UTF_8);
        long expected = Instant.parse("2026-11-10T02:00:00Z").getEpochSecond();
        assertThat(policy).contains("\"AWS:EpochTime\":" + expected + "}");
        assertThat(cookies.ttl()).isEqualTo(Duration.ofHours(2));
        assertThat(cookies.names())
                .containsExactlyInAnyOrder(
                        CloudFrontCookieSigner.POLICY,
                        CloudFrontCookieSigner.SIGNATURE,
                        CloudFrontCookieSigner.KEY_PAIR_ID);
    }

    @Test
    void 꺼짐은_아무_쿠키도_없다() {
        assertThat(NoAudioCookies.INSTANCE.issue()).isEmpty();
        assertThat(NoAudioCookies.INSTANCE.names()).isEmpty();
    }
}
