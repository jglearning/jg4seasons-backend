package com.jglearning.jg4seasons.infra.audio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 음원 쿠키 범위(ADR 0031 결정 6). 권 코드는 정책 JSON에 그대로 들어가므로 형식을 막는다 */
class AudioScopeTest {

    @Test
    void 권_코드를_그대로_가진다() {
        assertThat(AudioScope.book("L2-B1")).isEqualTo(new AudioScope.Book("L2-B1"));
        assertThat(AudioScope.book("K-B1")).isEqualTo(new AudioScope.Book("K-B1"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"", " ", "L2\"B1", "L2/B1", "L2-*", "L2-B?", "l2-b1", "L2-", "-B1", "L2--B1", "L2 B1", "L2\\B1"})
    void 권_코드_형식이_아니면_거부한다(String code) {
        assertThatThrownBy(() -> AudioScope.book(code))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("\"");
    }

    @Test
    void 권_코드가_null이면_거부한다() {
        assertThatThrownBy(() -> AudioScope.book(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 전체와_없음은_하나씩() {
        assertThat(AudioScope.all()).isSameAs(AudioScope.all());
        assertThat(AudioScope.none()).isSameAs(AudioScope.none());
        assertThat(AudioScope.all()).isNotEqualTo(AudioScope.none());
    }
}
