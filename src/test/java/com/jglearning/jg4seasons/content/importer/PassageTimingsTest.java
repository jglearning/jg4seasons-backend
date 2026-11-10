package com.jglearning.jg4seasons.content.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jglearning.jg4seasons.content.api.LessonContent.TimeRange;
import java.util.List;
import org.junit.jupiter.api.Test;

class PassageTimingsTest {

    @Test
    void 문장_사이_간격을_고르게_나눈다() {
        // 간격 (5500 - 4500) / 2 = 500
        assertThat(PassageTimings.of(List.of(1000, 2000, 1500), 5500))
                .containsExactly(new TimeRange(0, 1000), new TimeRange(1500, 3500), new TimeRange(4000, 5500));
    }

    @Test
    void 마지막_문장_끝은_지문_길이와_같다() {
        // 간격 1/3ms씩 나누어떨어지지 않는다
        List<TimeRange> timings = PassageTimings.of(List.of(1000, 1000, 1000, 1000), 4001);

        assertThat(timings).hasSize(4);
        assertThat(timings.getFirst().startMs()).isZero();
        assertThat(timings.getLast().endMs()).isEqualTo(4001);
        for (int i = 1; i < timings.size(); i++) {
            assertThat(timings.get(i).startMs())
                    .isGreaterThanOrEqualTo(timings.get(i - 1).endMs());
        }
    }

    @Test
    void 문장이_하나면_0부터_지문_길이까지() {
        assertThat(PassageTimings.of(List.of(2000), 2000)).containsExactly(new TimeRange(0, 2000));
        assertThatThrownBy(() -> PassageTimings.of(List.of(1900), 2000)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 간격이_음수면_거부() {
        assertThatThrownBy(() -> PassageTimings.of(List.of(3000, 3000), 5000))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 간격이_3000ms를_넘으면_거부() {
        assertThatThrownBy(() -> PassageTimings.of(List.of(1000, 1000), 5100))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(PassageTimings.of(List.of(1000, 1000), 5000))
                .containsExactly(new TimeRange(0, 1000), new TimeRange(4000, 5000));
    }

    @Test
    void 빈_문장_목록은_거부() {
        assertThatThrownBy(() -> PassageTimings.of(List.of(), 1000)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 영ms_문장은_거부() {
        assertThatThrownBy(() -> PassageTimings.of(List.of(1000, 0, 1000), 3000))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
