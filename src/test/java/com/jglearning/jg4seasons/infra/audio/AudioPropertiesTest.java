package com.jglearning.jg4seasons.infra.audio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** 음원 주소 = 접두어 + file_key. 접두어는 "/"로 끝나고, file_key는 "/"로 시작하지 않는다(//audio/… 는 다른 호스트로 해석된다). */
class AudioPropertiesTest {

    @Test
    void 접두어와_파일_경로를_붙인다() {
        assertThat(new AudioProperties("/").urlOf("audio/L2-B1-05/L2-B1-05_p1_1.0.mp3"))
                .isEqualTo("/audio/L2-B1-05/L2-B1-05_p1_1.0.mp3");
    }

    @Test
    void 접두어가_비었거나_슬래시로_끝나지_않으면_설정_오류() {
        assertThatThrownBy(() -> new AudioProperties("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AudioProperties(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AudioProperties("https://cdn.example"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 파일_경로가_슬래시로_시작하면_데이터_오류() {
        assertThatThrownBy(() -> new AudioProperties("/").urlOf("/audio/x.mp3"))
                .isInstanceOf(IllegalStateException.class);
    }
}
