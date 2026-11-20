package com.jglearning.jg4seasons.listening.passage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jglearning.jg4seasons.content.api.LessonContent;
import com.jglearning.jg4seasons.listening.segment.MergedRanges;
import com.jglearning.jg4seasons.listening.segment.Range;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * 한 목소리의 청취 구간을 다른 목소리의 시간축으로 옮기기(ADR 0034). 지문 음원을 조각(앞 여백·문장·간격·…·뒤 여백)으로 나누고 같은 조각의 같은 비율 지점으로 보낸다.
 */
class PassageAxisTest {

    /** 문장 3000ms × 3, 간격 500ms */
    static final VoiceAxis A = axis(10_000, 0, 3_000, 3_500, 6_500, 7_000, 10_000);
    /** 문장 4000·2000·6000ms, 간격 1000ms — A와 문장 길이 비율도 간격도 다르다 */
    static final VoiceAxis B = axis(14_000, 0, 4_000, 5_000, 7_000, 8_000, 14_000);
    /** 간격이 0인 목소리 */
    static final VoiceAxis Z = axis(9_000, 0, 3_000, 3_000, 6_000, 6_000, 9_000);

    static VoiceAxis axis(int durationMs, int... bounds) {
        List<Range> sentences = new ArrayList<>();
        for (int i = 0; i < bounds.length; i += 2) {
            sentences.add(new Range(bounds[i], bounds[i + 1]));
        }
        return new VoiceAxis(durationMs, sentences);
    }

    static Range range(int startMs, int endMs) {
        return new Range(startMs, endMs);
    }

    @Test
    void 같은_시간축이면_그대로() {
        assertThat(PassageAxis.map(A, A, range(1_234, 5_678))).contains(range(1_234, 5_678));
        assertThat(PassageAxis.map(B, B, range(0, 14_000))).contains(range(0, 14_000));
    }

    @Test
    void 문장의_같은_비율_지점으로_간다() {
        // A의 문장 1(3500~6500)의 0%·40%·100% = 3500·4700·6500 → B의 문장 1(5000~7000)의 5000·5800·7000
        assertThat(PassageAxis.map(A, B, range(3_500, 4_700))).contains(range(5_000, 5_800));
        assertThat(PassageAxis.map(A, B, range(4_700, 6_500))).contains(range(5_800, 7_000));
        assertThat(PassageAxis.map(B, A, range(5_800, 7_000))).as("거꾸로").contains(range(4_700, 6_500));
    }

    @Test
    void 간격은_간격으로_간다() {
        // A의 간격 0(3000~3500)의 절반 = 3250 → B의 간격 0(4000~5000)의 절반 = 4500
        assertThat(PassageAxis.map(A, B, range(3_000, 3_250))).contains(range(4_000, 4_500));
        assertThat(PassageAxis.map(A, B, range(3_250, 3_500))).contains(range(4_500, 5_000));
    }

    @Test
    void 처음과_끝은_처음과_끝으로() {
        assertThat(PassageAxis.map(A, B, range(0, 10_000))).contains(range(0, 14_000));
        assertThat(PassageAxis.map(B, A, range(0, 14_000))).contains(range(0, 10_000));
    }

    @Test
    void 단조이고_넓어지지_않는다() {
        // 이웃한 구간들은 옮긴 뒤에도 순서가 같고 서로 겹치지 않는다. 안쪽 반올림이라 길이 합이 대상 길이를 넘지 않는다
        for (VoiceAxis[] pair : new VoiceAxis[][] {{A, B}, {B, A}, {A, Z}, {Z, B}}) {
            VoiceAxis from = pair[0];
            VoiceAxis to = pair[1];
            int previousEnd = 0;
            long total = 0;
            for (int start = 0; start < from.durationMs(); start += 137) {
                int end = Math.min(start + 137, from.durationMs());
                Optional<Range> moved = PassageAxis.map(from, to, range(start, end));
                if (moved.isEmpty()) {
                    continue;
                }
                assertThat(moved.get().startMs()).as("%d~%d의 시작", start, end).isGreaterThanOrEqualTo(previousEnd);
                assertThat(moved.get().endMs()).isLessThanOrEqualTo(to.durationMs());
                previousEnd = moved.get().endMs();
                total += moved.get().endMs() - moved.get().startMs();
            }
            assertThat(total).isLessThanOrEqualTo(to.durationMs());
        }
    }

    @Test
    void 왕복은_원래_구간_안에_있고_2ms까지만_줄어든다() {
        List<Range> samples = List.of(
                range(0, 10_000),
                range(1, 2_999),
                range(1_234, 5_678),
                range(3_000, 3_500),
                range(3_101, 3_399),
                range(4_700, 9_001),
                range(6_500, 7_000),
                range(9_998, 10_000));
        for (Range original : samples) {
            Range there = PassageAxis.map(A, B, original).orElseThrow();
            Range back = PassageAxis.map(B, A, there).orElseThrow();

            assertThat(back.startMs()).as("%s 시작", original).isBetween(original.startMs(), original.startMs() + 2);
            assertThat(back.endMs()).as("%s 끝", original).isBetween(original.endMs() - 2, original.endMs());
        }
    }

    @Test
    void 앞_절반과_뒤_절반을_다른_목소리로_들어도_합치면_다_덮는다() {
        // A로 문장 0·간격 0·문장 1의 앞 절반(0~5000), B로 문장 1의 뒤 절반부터 끝까지(6000~14000)
        List<Range> heardInA = List.of(range(0, 5_000));
        List<Range> heardInB = PassageAxis.mapAll(B, A, List.of(range(6_000, 14_000)));

        assertThat(heardInB).containsExactly(range(5_000, 10_000));
        assertThat(MergedRanges.merge(heardInA, heardInB, A.durationMs())).containsExactly(range(0, 10_000));
    }

    @Test
    void 간격이_0인_경계에서_시작점은_뒤_조각의_처음_끝점은_앞_조각의_끝() {
        // Z의 3000은 문장 0의 끝이자 문장 1의 시작. A에는 그 사이에 간격(3000~3500)이 있다
        assertThat(PassageAxis.map(Z, A, range(0, 3_000))).as("3000에서 끝남").contains(range(0, 3_000));
        assertThat(PassageAxis.map(Z, A, range(3_000, 6_000))).as("3000에서 시작").contains(range(3_500, 6_500));
        assertThat(PassageAxis.map(Z, A, range(1_500, 4_500)))
                .as("3000을 가로지르면 간격까지 덮는다")
                .contains(range(1_500, 5_000));
    }

    @Test
    void 간격만_들은_구간은_간격이_없는_목소리에서_사라진다() {
        assertThat(PassageAxis.map(A, Z, range(3_000, 3_500))).isEmpty();
        assertThat(PassageAxis.map(A, Z, range(2_000, 3_500))).as("문장 끝 + 간격").contains(range(2_000, 3_000));
    }

    @Test
    void 문장_수가_다르면_거부한다() {
        VoiceAxis two = axis(6_500, 0, 3_000, 3_500, 6_500);

        assertThatThrownBy(() -> PassageAxis.map(two, A, range(0, 1_000)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("문장 수");
        assertThatThrownBy(() -> PassageAxis.map(A, two, range(0, 1_000))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 문장_시각이_없으면_길이_비율로_옮긴다() {
        VoiceAxis noTimings = new VoiceAxis(10_000, List.of());

        assertThat(PassageAxis.map(noTimings, B, range(2_500, 5_000))).contains(range(3_500, 7_000));
        assertThat(PassageAxis.map(B, noTimings, range(3_500, 7_000)))
                .as("대상에 시각이 없어도")
                .contains(range(2_500, 5_000));
    }

    @Test
    void 너무_짧아_사라지는_구간은_빈_값이고_mapAll은_뺀다() {
        // B의 문장 2(6000ms)는 A에서 3000ms — 1ms는 안쪽 반올림으로 길이 0
        assertThat(PassageAxis.map(B, A, range(8_001, 8_002))).isEmpty();
        assertThat(PassageAxis.mapAll(B, A, List.of(range(0, 4_000), range(8_001, 8_002), range(8_000, 14_000))))
                .containsExactly(range(0, 3_000), range(7_000, 10_000));
    }

    @Test
    void 길이를_넘는_구간은_길이까지만_본다() {
        assertThat(PassageAxis.map(A, B, range(9_000, 10_800))).contains(range(12_000, 14_000));
        assertThat(PassageAxis.map(A, B, range(10_000, 10_800))).isEmpty();
    }

    @Test
    void 시간축은_겹치거나_길이를_넘는_문장을_거부한다() {
        assertThatThrownBy(() -> axis(10_000, 0, 3_000, 2_999, 6_000)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> axis(5_000, 0, 3_000, 3_500, 6_500)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new VoiceAxis(0, List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 목소리_트랙에서_시간축을_만든다() {
        LessonContent.Voice voice = new LessonContent.Voice(
                7L,
                "uk_m",
                "audio/x.mp3",
                14_000,
                List.of(
                        new LessonContent.TimeRange(0, 4_000),
                        new LessonContent.TimeRange(5_000, 7_000),
                        new LessonContent.TimeRange(8_000, 14_000)),
                false);

        assertThat(VoiceAxis.of(voice)).isEqualTo(B);
    }

    @Test
    void 앞뒤_여백은_여백으로_간다() {
        // 지금 콘텐츠는 여백이 0이지만(첫 문장 0에서 시작, 마지막 문장이 길이에서 끝) 시간축은 여백을 허용한다
        VoiceAxis p = axis(10_000, 500, 3_000, 3_500, 9_000); // 앞 500, 뒤 1000
        VoiceAxis q = axis(12_000, 1_000, 5_000, 6_000, 11_000); // 앞 1000, 뒤 1000
        VoiceAxis none = axis(6_500, 0, 3_000, 3_500, 6_500); // 여백 없음

        assertThat(PassageAxis.map(p, q, range(0, 500))).as("앞 여백 전체").contains(range(0, 1_000));
        assertThat(PassageAxis.map(p, q, range(250, 9_500)))
                .as("앞 여백의 절반부터 뒤 여백의 절반까지")
                .contains(range(500, 11_500));
        assertThat(PassageAxis.map(p, q, range(9_000, 10_000))).as("뒤 여백 전체").contains(range(11_000, 12_000));
        assertThat(PassageAxis.map(p, none, range(0, 500)))
                .as("대상에 앞 여백이 없으면 사라진다")
                .isEmpty();
        assertThat(PassageAxis.map(none, p, range(0, 6_500)))
                .as("여백이 없는 쪽의 전체 = 문장 0의 시작부터 문장 1의 끝까지")
                .contains(range(500, 9_000));
    }

    @Test
    void 문장_시각이_음원_길이를_넘는_트랙은_트랙_id와_함께_데이터_오류() {
        LessonContent.Voice broken = new LessonContent.Voice(
                77L,
                "uk_m",
                "audio/x.mp3",
                14_000,
                List.of(new LessonContent.TimeRange(0, 4_000), new LessonContent.TimeRange(5_000, 15_000)),
                false);

        assertThatThrownBy(() -> VoiceAxis.of(broken))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("track 77")
                .hasMessageContaining("uk_m")
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }
}
