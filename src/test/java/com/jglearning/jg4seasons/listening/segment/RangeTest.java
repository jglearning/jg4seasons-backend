package com.jglearning.jg4seasons.listening.segment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** 청취 구간 불변식: 0 <= 시작 < 끝(listening_segment CHECK와 같음). flush의 세그먼트 병합이 이 타입을 그대로 쓴다. */
class RangeTest {

    @Test
    void 시작은_0_이상이고_끝보다_앞() {
        assertThat(new Range(0, 1)).isEqualTo(new Range(0, 1));
        assertThatThrownBy(() -> new Range(-1, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Range(10, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Range(5000, 100)).isInstanceOf(IllegalArgumentException.class);
    }
}
