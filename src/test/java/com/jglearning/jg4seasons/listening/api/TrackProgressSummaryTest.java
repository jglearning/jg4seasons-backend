package com.jglearning.jg4seasons.listening.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** 트랙 집계 값 범위: 어긋난 값은 데이터 오류로 막는다(교사 화면에 그대로 나가지 않게). */
class TrackProgressSummaryTest {

    static final Instant AT = Instant.parse("2026-11-10T01:00:00Z");

    @Test
    void 경계값은_받는다() {
        assertThat(new TrackProgressSummary(BigDecimal.ZERO, false, 0, 0, AT).coverage())
                .isZero();
        assertThat(new TrackProgressSummary(BigDecimal.ONE, true, 1, 1, AT).completed())
                .isTrue();
    }

    @Test
    void 청취율이_0_1_밖이면_데이터_오류() {
        assertThatThrownBy(() -> new TrackProgressSummary(new BigDecimal("1.0001"), true, 0, 0, AT))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new TrackProgressSummary(new BigDecimal("-0.1"), false, 0, 0, AT))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 재생_시간이나_횟수가_음수면_데이터_오류() {
        assertThatThrownBy(() -> new TrackProgressSummary(BigDecimal.ZERO, false, -1, 0, AT))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new TrackProgressSummary(BigDecimal.ZERO, false, 0, -1, AT))
                .isInstanceOf(IllegalStateException.class);
    }
}
