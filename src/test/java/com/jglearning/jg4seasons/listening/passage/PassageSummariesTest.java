package com.jglearning.jg4seasons.listening.passage;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.content.api.LessonContent;
import com.jglearning.jg4seasons.listening.completion.CompletionRule;
import com.jglearning.jg4seasons.listening.segment.Range;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 화면·응답에 나가는 지문 값(ADR 0034 결정 5·7). 바닥 규칙: 지문 집계가 없거나 기준이 달라 쓸 수 없어도 목소리별 집계로 완료·청취율이 나간다.
 */
class PassageSummariesTest {

    static final long US_W = 11L;
    static final long UK_M = 12L;

    static final LessonContent.Passage PASSAGE = PassageFixtures.passage(
            1L,
            PassageFixtures.voice(US_W, "us_w", 10_000, 0, 3_000, 3_500, 6_500, 7_000, 10_000),
            PassageFixtures.voice(UK_M, "uk_m", 14_000, 0, 4_000, 5_000, 7_000, 8_000, 14_000));

    static final CompletionRule RULE = PassageFixtures.rule(Set.of("us_w", "us_m", "uk_w", "uk_m"), true);

    static StoredPassage stored(long refTrackId, int refDurationMs, String coverage, boolean completed) {
        return new StoredPassage(
                refTrackId, refDurationMs, List.of(new Range(0, 5_000)), new BigDecimal(coverage), completed);
    }

    static TrackState track(String coverage, boolean completed) {
        return new TrackState(new BigDecimal(coverage), completed);
    }

    @Test
    void 아무것도_없으면_0이고_미완료() {
        PassageSummary summary = PassageSummaries.of(PASSAGE, RULE, Optional.empty(), Map.of());

        assertThat(summary.coverage()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.completed()).isFalse();
    }

    @Test
    void 지문_집계가_없으면_목소리_청취율_중_최댓값() {
        PassageSummary summary = PassageSummaries.of(
                PASSAGE, RULE, Optional.empty(), Map.of(US_W, track("0.3", false), UK_M, track("0.55", false)));

        assertThat(summary.coverage()).isEqualByComparingTo("0.55");
        assertThat(summary.completed()).isFalse();
    }

    @Test
    void 목소리_하나가_완료면_지문_집계가_없어도_완료() {
        PassageSummary summary = PassageSummaries.of(PASSAGE, RULE, Optional.empty(), Map.of(UK_M, track("0.9", true)));

        assertThat(summary.completed()).isTrue();
        assertThat(summary.coverage()).isEqualByComparingTo("0.9");
    }

    @Test
    void 쓸_수_있는_지문_집계면_합친_값과_목소리_최댓값_중_큰_쪽() {
        assertThat(PassageSummaries.of(
                                PASSAGE,
                                RULE,
                                Optional.of(stored(US_W, 10_000, "0.8", false)),
                                Map.of(US_W, track("0.5", false), UK_M, track("0.4", false)))
                        .coverage())
                .as("합친 값이 크다")
                .isEqualByComparingTo("0.8");
        // 한 목소리로 기준만큼 들었는데 기준 시간축으로 옮긴 값이 조금 낮은 경우 — 완료인데 기준보다 낮은 숫자가 나가지 않는다
        PassageSummary single = PassageSummaries.of(
                PASSAGE, RULE, Optional.of(stored(US_W, 10_000, "0.8930", false)), Map.of(UK_M, track("0.9", true)));
        assertThat(single.coverage()).as("목소리 값이 크다").isEqualByComparingTo("0.9");
        assertThat(single.completed()).isTrue();
    }

    @Test
    void 지문_집계가_완료면_목소리가_모두_미완료여도_완료() {
        PassageSummary summary = PassageSummaries.of(
                PASSAGE,
                RULE,
                Optional.of(stored(US_W, 10_000, "1", true)),
                Map.of(US_W, track("0.5", false), UK_M, track("0.5714", false)));

        assertThat(summary.completed()).isTrue();
        assertThat(summary.coverage()).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void 기준_길이가_다르면_지문_집계의_청취율을_쓰지_않고_완료는_유지한다() {
        StoredPassage stale = stored(US_W, 9_000, "1", true);

        assertThat(PassageSummaries.usable(stale, PassageCombiner.reference(PASSAGE, RULE)))
                .isFalse();
        PassageSummary summary =
                PassageSummaries.of(PASSAGE, RULE, Optional.of(stale), Map.of(US_W, track("0.5", false)));
        assertThat(summary.coverage()).as("옛 시간축 값은 버린다").isEqualByComparingTo("0.5");
        assertThat(summary.completed()).as("완료는 한 번 되면 유지").isTrue();
    }

    @Test
    void 기준_트랙이_다르면_지문_집계의_청취율을_쓰지_않는다() {
        StoredPassage stale = stored(UK_M, 10_000, "0.95", false);

        assertThat(PassageSummaries.usable(stale, PassageCombiner.reference(PASSAGE, RULE)))
                .isFalse();
        assertThat(PassageSummaries.of(PASSAGE, RULE, Optional.of(stale), Map.of(US_W, track("0.2", false)))
                        .coverage())
                .isEqualByComparingTo("0.2");
    }

    @Test
    void 기준이_같으면_쓸_수_있다() {
        assertThat(PassageSummaries.usable(
                        stored(US_W, 10_000, "0.5", false), PassageCombiner.reference(PASSAGE, RULE)))
                .isTrue();
    }

    @Test
    void 필수가_아닌_목소리의_완료와_청취율은_보지_않는다() {
        CompletionRule onlyUsW = PassageFixtures.rule(Set.of("us_w"), true);

        PassageSummary summary = PassageSummaries.of(
                PASSAGE, onlyUsW, Optional.empty(), Map.of(US_W, track("0.1", false), UK_M, track("1", true)));

        assertThat(summary.completed()).isFalse();
        assertThat(summary.coverage()).isEqualByComparingTo("0.1");
    }
}
