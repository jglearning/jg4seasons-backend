package com.jglearning.jg4seasons.listening.passage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jglearning.jg4seasons.content.api.LessonContent;
import com.jglearning.jg4seasons.listening.completion.CompletionRule;
import com.jglearning.jg4seasons.listening.segment.Range;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 목소리별 들은 구간을 지문의 기준 시간축에서 합치기(ADR 0034). 픽스처는 PassageAxisTest의 A(10초)·B(14초)와 같다. */
class PassageCombinerTest {

    static final long US_W = 11L;
    static final long UK_M = 12L;
    static final long US_M = 13L;

    static final LessonContent.Passage PASSAGE = PassageFixtures.passage(
            1L,
            PassageFixtures.voice(US_W, "us_w", 10_000, 0, 3_000, 3_500, 6_500, 7_000, 10_000),
            PassageFixtures.voice(UK_M, "uk_m", 14_000, 0, 4_000, 5_000, 7_000, 8_000, 14_000),
            PassageFixtures.voice(US_M, "us_m", 10_000, 0, 3_000, 3_500, 6_500, 7_000, 10_000));

    static final CompletionRule ALL = PassageFixtures.rule(Set.of("us_w", "us_m", "uk_w", "uk_m"), true);

    static Range range(int startMs, int endMs) {
        return new Range(startMs, endMs);
    }

    @Test
    void 기준은_필수인_첫_목소리() {
        assertThat(PassageCombiner.reference(PASSAGE, ALL).orElseThrow().trackId())
                .isEqualTo(US_W);
        assertThat(PassageCombiner.reference(PASSAGE, PassageFixtures.rule(Set.of("uk_m", "us_m"), true))
                        .orElseThrow()
                        .trackId())
                .as("us_w가 필수가 아니면 그다음")
                .isEqualTo(UK_M);
    }

    @Test
    void 필수_목소리가_지문에_없으면_기준이_없다() {
        // 오류가 아니다 — 그 지문은 합치지 않고 지금까지처럼 완료될 수 없는 지문으로 남는다
        assertThat(PassageCombiner.reference(PASSAGE, PassageFixtures.rule(Set.of("uk_w"), true)))
                .isEmpty();
    }

    @Test
    void 문장_시각이_어긋난_목소리를_옮겨야_하면_트랙_id와_함께_데이터_오류() {
        // uk_m의 마지막 문장 끝(15000)이 음원 길이(14000)를 넘는다
        LessonContent.Passage broken = PassageFixtures.passage(
                3L,
                PassageFixtures.voice(US_W, "us_w", 10_000, 0, 3_000, 3_500, 6_500, 7_000, 10_000),
                PassageFixtures.voice(UK_M, "uk_m", 14_000, 0, 4_000, 5_000, 7_000, 8_000, 15_000));

        assertThat(combine(broken, ALL, Map.of(US_W, List.of(range(0, 5_000)))).mergedRanges())
                .as("어긋난 목소리를 안 들었으면 그 시간축은 만들지 않는다")
                .containsExactly(range(0, 5_000));
        assertThatThrownBy(() -> combine(broken, ALL, Map.of(UK_M, List.of(range(0, 5_000)))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("track " + UK_M)
                .hasMessageContaining("uk_m");
    }

    private static CombinedProgress combine(
            LessonContent.Passage passage, CompletionRule rule, Map<Long, List<Range>> merged) {
        return PassageCombiner.combine(
                passage, rule, PassageCombiner.reference(passage, rule).orElseThrow(), merged);
    }

    @Test
    void 두_목소리의_앞뒤를_합치면_전체() {
        CombinedProgress combined =
                combine(PASSAGE, ALL, Map.of(US_W, List.of(range(0, 5_000)), UK_M, List.of(range(6_000, 14_000))));

        assertThat(combined.refTrackId()).isEqualTo(US_W);
        assertThat(combined.refDurationMs()).isEqualTo(10_000);
        assertThat(combined.mergedRanges()).containsExactly(range(0, 10_000));
        assertThat(combined.coveredMs()).isEqualTo(10_000);
        assertThat(combined.coverage()).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void 같은_자리를_두_목소리로_들어도_늘지_않는다() {
        // uk_m의 0~5800 = 문장 0 + 간격 0 + 문장 1의 40% = 기준 시간축의 0~4700 — us_w로 들은 0~5000 안
        CombinedProgress combined =
                combine(PASSAGE, ALL, Map.of(US_W, List.of(range(0, 5_000)), UK_M, List.of(range(0, 5_800))));

        assertThat(combined.mergedRanges()).containsExactly(range(0, 5_000));
        assertThat(combined.coverage()).isEqualByComparingTo("0.5");
    }

    @Test
    void 기준이_아닌_목소리로만_들어도_기준_시간축으로_적는다() {
        CombinedProgress combined = combine(PASSAGE, ALL, Map.of(UK_M, List.of(range(5_000, 5_800))));

        assertThat(combined.refTrackId()).isEqualTo(US_W);
        assertThat(combined.mergedRanges()).containsExactly(range(3_500, 4_700));
        assertThat(combined.coveredMs()).isEqualTo(1_200);
        assertThat(combined.coverage()).isEqualByComparingTo("0.12");
    }

    @Test
    void 길이가_같은_목소리는_그대로_합친다() {
        CombinedProgress combined =
                combine(PASSAGE, ALL, Map.of(US_W, List.of(range(0, 2_000)), US_M, List.of(range(8_000, 10_000))));

        assertThat(combined.mergedRanges()).containsExactly(range(0, 2_000), range(8_000, 10_000));
        assertThat(combined.coveredMs()).isEqualTo(4_000);
    }

    @Test
    void 필수가_아닌_목소리는_합치지_않는다() {
        CompletionRule onlyUsW = PassageFixtures.rule(Set.of("us_w"), true);

        CombinedProgress combined =
                combine(PASSAGE, onlyUsW, Map.of(US_W, List.of(range(0, 5_000)), UK_M, List.of(range(6_000, 14_000))));

        assertThat(combined.mergedRanges()).containsExactly(range(0, 5_000));
    }

    @Test
    void 안_들은_지문은_빈_구간과_0() {
        CombinedProgress combined = combine(PASSAGE, ALL, Map.of());

        assertThat(combined.mergedRanges()).isEmpty();
        assertThat(combined.coveredMs()).isZero();
        assertThat(combined.coverage()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(combined.refTrackId()).isEqualTo(US_W);
    }

    @Test
    void 문장_시각이_없는_목소리는_길이_비율로_합친다() {
        LessonContent.Passage passage = PassageFixtures.passage(
                2L,
                PassageFixtures.voice(US_W, "us_w", 10_000, 0, 3_000, 3_500, 6_500, 7_000, 10_000),
                PassageFixtures.voice(UK_M, "uk_m", 14_000));

        CombinedProgress combined =
                combine(passage, ALL, Map.of(US_W, List.of(range(0, 5_000)), UK_M, List.of(range(7_000, 14_000))));

        assertThat(combined.mergedRanges()).containsExactly(range(0, 10_000));
    }
}
