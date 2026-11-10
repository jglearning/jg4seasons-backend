package com.jglearning.jg4seasons.infra.audio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class AudioSigningPropertiesTest {

    static final String KEY = "c2VjcmV0LWtleS1ieXRlcw";
    static final String RESOURCE = "https://site.test/audio/*";

    @Test
    void 꺼져_있으면_빈_칸을_허용한다() {
        AudioSigningProperties off = new AudioSigningProperties(false, null, null, null, Duration.ofHours(2));

        assertThat(off.enabled()).isFalse();
    }

    @Test
    void 켜졌는데_빈_칸이_있으면_칸_이름으로_거부() {
        assertThatThrownBy(() -> new AudioSigningProperties(true, " ", KEY, RESOURCE, Duration.ofHours(2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("key-pair-id");
        assertThatThrownBy(() -> new AudioSigningProperties(true, "K1", "", RESOURCE, Duration.ofHours(2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("private-key");
        assertThatThrownBy(() -> new AudioSigningProperties(true, "K1", KEY, null, Duration.ofHours(2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("resource");
    }

    @Test
    void resource는_https로_시작하고_따옴표가_없어야_한다() {
        assertThatThrownBy(() -> new AudioSigningProperties(true, "K1", KEY, "site.test/audio/*", Duration.ofHours(2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("resource");
        assertThatThrownBy(() ->
                        new AudioSigningProperties(true, "K1", KEY, "https://site.test/\"audio/*", Duration.ofHours(2)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        new AudioSigningProperties(true, "K1", KEY, "https://site.test/\\audio/*", Duration.ofHours(2)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        new AudioSigningProperties(true, "K1", KEY, "http://site.test/audio/*", Duration.ofHours(2)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resource는_쿠키_Path와_짝인_audio_별로_끝나야_한다() {
        // CloudFront는 Resource가 요청 URL과 맞지 않으면 모든 음원을 403으로 막는다 — 서버가 뜰 때 막는다
        for (String wrong :
                new String[] {"https://site.test/audio", "https://site.test/audio/", "https://site.test/aduio/*"}) {
            assertThatThrownBy(() -> new AudioSigningProperties(true, "K1", KEY, wrong, Duration.ofHours(2)))
                    .as(wrong)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("resource");
        }
        assertThat(new AudioSigningProperties(true, "K1", KEY, "https://site.test/audio/*", Duration.ofHours(2))
                        .resource())
                .isEqualTo("https://site.test/audio/*");
    }

    @Test
    void 키_쌍_ID는_대문자와_숫자만() {
        // CloudFront 공개 키 ID 형식(K…). 쿠키 값에 못 쓰는 ; , 공백이 섞이면 로그인마다 500이 된다
        for (String wrong : new String[] {"K1;x", "K1,2", "k1abc", "K1-2"}) {
            assertThatThrownBy(() -> new AudioSigningProperties(true, wrong, KEY, RESOURCE, Duration.ofHours(2)))
                    .as(wrong)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("key-pair-id");
        }
    }

    @Test
    void 수명은_0보다_커야_한다() {
        assertThatThrownBy(() -> new AudioSigningProperties(false, null, null, null, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ttl");
    }

    @Test
    void toString에_개인_키가_없다() {
        AudioSigningProperties on = new AudioSigningProperties(true, "K1", KEY, RESOURCE, Duration.ofHours(2));

        assertThat(on.toString()).doesNotContain(KEY).contains("K1").contains(RESOURCE);
    }
}
