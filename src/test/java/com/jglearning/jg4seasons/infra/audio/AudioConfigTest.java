package com.jglearning.jg4seasons.infra.audio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jglearning.jg4seasons.MutableClock;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** 서명 켜짐·꺼짐에 따른 빈 선택(ADR 0028). 스프링 컨텍스트 없이 설정 메서드를 직접 부른다(test 프로파일은 꺼짐) */
class AudioConfigTest {

    static KeyPair keys;

    @BeforeAll
    static void keys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keys = generator.generateKeyPair();
    }

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-10T00:00:00Z"));

    @Test
    void 꺼져_있으면_음원_쿠키가_없다() {
        AudioCookies cookies = new AudioConfig()
                .audioCookies(new AudioSigningProperties(false, null, null, null, Duration.ofHours(2)), clock);

        assertThat(cookies).isSameAs(NoAudioCookies.INSTANCE);
    }

    @Test
    void 켜지면_설정값으로_서명한다() throws Exception {
        String key = Base64.getEncoder().encodeToString(keys.getPrivate().getEncoded());
        // 기본 2시간과 다른 수명이어야 고정값 실수를 잡는다
        AudioSigningProperties signing =
                new AudioSigningProperties(true, "KCONFIG1", key, "https://site.test/audio/*", Duration.ofHours(3));

        AudioCookies cookies = new AudioConfig().audioCookies(signing, clock);
        Map<String, String> issued = cookies.issue(AudioScope.all());

        assertThat(cookies.ttl()).isEqualTo(Duration.ofHours(3));
        assertThat(issued).containsEntry("CloudFront-Key-Pair-Id", "KCONFIG1");
        String policy = new String(decode(issued.get("CloudFront-Policy")), StandardCharsets.UTF_8);
        assertThat(policy)
                .contains("\"Resource\":\"https://site.test/audio/*\"")
                .contains("\"AWS:EpochTime\":"
                        + Instant.parse("2026-11-10T03:00:00Z").getEpochSecond() + "}");
        Signature verifier = Signature.getInstance("SHA1withRSA");
        verifier.initVerify(keys.getPublic());
        verifier.update(policy.getBytes(StandardCharsets.UTF_8));
        assertThat(verifier.verify(decode(issued.get("CloudFront-Signature")))).isTrue();
    }

    @Test
    void 권_범위는_설정_호스트의_권_패턴으로_서명한다() {
        String key = Base64.getEncoder().encodeToString(keys.getPrivate().getEncoded());
        AudioSigningProperties signing =
                new AudioSigningProperties(true, "KCONFIG1", key, "https://site.test/audio/*", Duration.ofHours(2));

        AudioCookies cookies = new AudioConfig().audioCookies(signing, clock);

        String policy = new String(
                decode(cookies.issue(AudioScope.book("L2-B1")).get("CloudFront-Policy")), StandardCharsets.UTF_8);
        assertThat(policy).contains("\"Resource\":\"https://site.test/audio/L2-B1-*\"");
        assertThat(cookies.issue(AudioScope.none())).isEmpty();
    }

    @Test
    void 켜졌는데_키를_못_읽으면_빈_생성이_실패한다() {
        AudioSigningProperties signing = new AudioSigningProperties(
                true, "KCONFIG1", "bm90LWEta2V5", "https://site.test/audio/*", Duration.ofHours(2));

        assertThatThrownBy(() -> new AudioConfig().audioCookies(signing, clock))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("bm90LWEta2V5");
    }

    private static byte[] decode(String value) {
        return Base64.getDecoder()
                .decode(value.replace('-', '+').replace('_', '=').replace('~', '/'));
    }
}
