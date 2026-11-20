package com.jglearning.jg4seasons.listening.passage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

        assertThat(stale.on(PassageCombiner.reference(PASSAGE, RULE).orElseThrow()))
                .isEmpty();
        PassageSummary summary =
                PassageSummaries.of(PASSAGE, RULE, Optional.of(stale), Map.of(US_W, track("0.5", false)));
        assertThat(summary.coverage()).as("옛 시간축 값은 버린다").isEqualByComparingTo("0.5");
        assertThat(summary.completed()).as("완료는 한 번 되면 유지").isTrue();
    }

    @Test
    void 기준_트랙이_다르면_지문_집계의_청취율을_쓰지_않는다() {
        StoredPassage stale = stored(UK_M, 10_000, "0.95", false);

        assertThat(stale.on(PassageCombiner.reference(PASSAGE, RULE).orElseThrow()))
                .isEmpty();
        assertThat(PassageSummaries.of(PASSAGE, RULE, Optional.of(stale), Map.of(US_W, track("0.2", false)))
                        .coverage())
                .isEqualByComparingTo("0.2");
    }

    @Test
    void 기준이_같으면_합친_구간을_어느_목소리의_시간축으로든_꺼낼_수_있다() {
        LessonContent.Voice reference = PassageCombiner.reference(PASSAGE, RULE).orElseThrow();
        StoredPassage.OnReference usable =
                stored(US_W, 10_000, "0.5", false).on(reference).orElseThrow();

        assertThat(usable.coverage()).isEqualByComparingTo("0.5");
        assertThat(usable.rangesOn(reference)).containsExactly(new Range(0, 5_000));
        // us_w의 0~5000(문장 0 + 간격 0 + 문장 1의 절반) = uk_m의 0~6000
        assertThat(usable.rangesOn(PASSAGE.voices().get(1))).containsExactly(new Range(0, 6_000));
    }

    @Test
    void 범위를_벗어난_청취율은_데이터_오류() {
        assertThatThrownBy(() -> stored(US_W, 10_000, "1.2", false)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> stored(US_W, 0, "0.5", false)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new PassageSummary(new BigDecimal("-0.1"), false))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 필수_트랙_완료_여부는_이_지문의_필수_목소리만_본다() {
        assertThat(PassageSummaries.requiredTrackCompleted(PASSAGE, RULE, Map.of(UK_M, track("0.9", true))))
                .isTrue();
        assertThat(PassageSummaries.requiredTrackCompleted(PASSAGE, RULE, Map.of(UK_M, track("0.89", false))))
                .isFalse();
        assertThat(PassageSummaries.requiredTrackCompleted(
                        PASSAGE, PassageFixtures.rule(Set.of("us_w"), true), Map.of(UK_M, track("1", true))))
                .as("필수가 아닌 목소리의 완료")
                .isFalse();
        assertThat(PassageSummaries.requiredTrackCompleted(PASSAGE, RULE, Map.of(999L, track("1", true))))
                .as("다른 지문의 트랙")
                .isFalse();
    }

    @Test
    void 필수가_아닌_목소리의_완료와_청취율은_보지_않는다() {
        CompletionRule onlyUsW = PassageFixtures.rule(Set.of("us_w"), true);

        PassageSummary summary = PassageSummaries.of(
                PASSAGE, onlyUsW, Optional.empty(), Map.of(US_W, track("0.1", false), UK_M, track("1", true)));

        assertThat(summary.completed()).isFalse();
        assertThat(summary.coverage()).isEqualByComparingTo("0.1");
    }

    @Test
    void 필수_목소리_트랙이_없는_지문은_저장_행이_있어도_오류_없이_바닥_값() {
        // 규칙의 필수 목소리를 좁힌 뒤에 남은 옛 행 — 기준을 구할 수 없으니 구간·청취율은 쓰지 않고 완료만 믿는다
        CompletionRule noneHere = PassageFixtures.rule(Set.of("uk_w"), true);

        PassageSummary summary = PassageSummaries.of(
                PASSAGE, noneHere, Optional.of(stored(US_W, 10_000, "1", true)), Map.of(US_W, track("1", true)));

        assertThat(summary.completed()).as("저장된 완료는 유지").isTrue();
        assertThat(summary.coverage()).as("필수가 아닌 목소리의 청취율도, 옛 합친 값도 쓰지 않는다").isEqualByComparingTo("0");
    }

    @Test
    void 합산이_꺼진_규칙이면_저장_행의_청취율을_쓰지_않고_완료만_믿는다() {
        // 규칙을 끄면 행이 더 갱신되지 않는다 — 기준이 같아도 끈 시점의 값이다
        CompletionRule off = PassageFixtures.rule(Set.of("us_w", "us_m", "uk_w", "uk_m"), false);
        StoredPassage row = stored(US_W, 10_000, "0.85", true);

        assertThat(PassageSummaries.combined(PASSAGE, off, Optional.of(row))).isEmpty();
        assertThat(PassageSummaries.combined(PASSAGE, RULE, Optional.of(row))).isPresent();
        PassageSummary summary = PassageSummaries.of(PASSAGE, off, Optional.of(row), Map.of(US_W, track("0.4", false)));
        assertThat(summary.coverage()).isEqualByComparingTo("0.4");
        assertThat(summary.completed()).isTrue();
    }
}
