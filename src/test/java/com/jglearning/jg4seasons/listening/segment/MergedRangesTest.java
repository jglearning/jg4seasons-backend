package com.jglearning.jg4seasons.listening.segment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 청취 구간 병합(TRK-04, ERD track_progress 갱신 알고리즘): 시작 순 정렬, 겹치거나 200ms 이하로 붙으면 합침, 트랙 길이로 자름. 같은 구간이 여러 번 와도 결과가
 * 같다(TRK-06 멱등).
 */
class MergedRangesTest {

    static final int DURATION = 100_000;

    static Range r(int start, int end) {
        return new Range(start, end);
    }

    static List<Range> merge(List<Range> existing, List<Range> added) {
        return MergedRanges.merge(existing, added, DURATION);
    }

    @Test
    void 겹치면_합친다() {
        assertThat(merge(List.of(), List.of(r(0, 100), r(50, 200)))).containsExactly(r(0, 200));
    }

    @Test
    void 인접_200ms는_합친다() {
        assertThat(merge(List.of(), List.of(r(0, 100), r(300, 400)))).containsExactly(r(0, 400));
    }

    @Test
    void 간격_201ms는_떨어진다() {
        assertThat(merge(List.of(), List.of(r(0, 100), r(301, 400)))).containsExactly(r(0, 100), r(301, 400));
    }

    @Test
    void 끝과_시작이_같으면_합친다() {
        assertThat(merge(List.of(), List.of(r(0, 100), r(100, 200)))).containsExactly(r(0, 200));
    }

    @Test
    void 역순_입력도_정렬한다() {
        assertThat(merge(List.of(), List.of(r(5000, 6000), r(0, 1000), r(2000, 3000))))
                .containsExactly(r(0, 1000), r(2000, 3000), r(5000, 6000));
    }

    @Test
    void 같은_구간_두_번은_한_번() {
        List<Range> once = merge(List.of(), List.of(r(0, 1000)));

        assertThat(merge(once, List.of(r(0, 1000)))).isEqualTo(once);
        assertThat(merge(List.of(), List.of(r(0, 1000), r(0, 1000)))).containsExactly(r(0, 1000));
    }

    @Test
    void 포함된_구간은_흡수한다() {
        assertThat(merge(List.of(r(0, 10_000)), List.of(r(2000, 3000)))).containsExactly(r(0, 10_000));
    }

    @Test
    void 기존_구간_사이를_새_구간이_잇는다() {
        assertThat(merge(List.of(r(0, 100), r(1000, 1100)), List.of(r(100, 1000))))
                .containsExactly(r(0, 1100));
    }

    @Test
    void 하나가_여러_구간을_한꺼번에_덮는다() {
        assertThat(merge(List.of(r(0, 100), r(1000, 1100), r(5000, 5100)), List.of(r(50, 5050))))
                .containsExactly(r(0, 5100));
    }

    @Test
    void 길이를_넘으면_자른다() {
        assertThat(MergedRanges.merge(List.of(), List.of(r(900, 1900)), 1000)).containsExactly(r(900, 1000));
    }

    @Test
    void 길이_밖뿐이면_버린다() {
        assertThat(MergedRanges.merge(List.of(), List.of(r(1000, 1500)), 1000)).isEmpty();
        assertThat(MergedRanges.merge(List.of(), List.of(r(1200, 1500)), 1000)).isEmpty();
    }

    @Test
    void 빈_입력은_빈_결과() {
        assertThat(merge(List.of(), List.of())).isEmpty();
        assertThat(merge(List.of(r(0, 100)), List.of())).containsExactly(r(0, 100));
    }

    @Test
    void 순서를_바꿔_넣어도_결과가_같다() {
        List<Range> a = List.of(r(0, 1000), r(1150, 2000), r(4000, 4500));
        List<Range> b = List.of(r(1900, 4100), r(9000, 9100));

        assertThat(merge(merge(List.of(), a), b)).isEqualTo(merge(merge(List.of(), b), a));
    }

    @Test
    void 덮은_길이는_구간_길이의_합() {
        assertThat(MergedRanges.coveredMs(List.of(r(0, 1000), r(2000, 2500)))).isEqualTo(1500);
        assertThat(MergedRanges.coveredMs(List.of())).isZero();
    }

    @Test
    void 인접_기준은_200ms() {
        assertThat(MergedRanges.ADJACENT_MS).isEqualTo(200);
    }
}
