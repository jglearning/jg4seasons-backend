package com.jglearning.jg4seasons.listening.segment;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.listening.segment.SegmentFilter.Accepted;
import com.jglearning.jg4seasons.listening.segment.SegmentFilter.IncomingSegment;
import com.jglearning.jg4seasons.listening.segment.SegmentFilter.Result;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * flush 세그먼트 검증(03 §3.1): 0 <= start < end <= 길이 + 1000, 길이 <= 60,000ms, 요청당 50개. 어기면 버리고 dropped로 센다(요청은 200). 같은
 * 세션에서 이미 받은 구간·같은 요청 안의 중복은 조용히 뺀다(결정 C — 정상 재전송이라 dropped 아님).
 */
class SegmentFilterTest {

    static final int DURATION = 10_000;
    static final Instant AT = Instant.parse("2026-11-10T03:00:00Z");

    static IncomingSegment seg(Integer start, Integer end) {
        return new IncomingSegment(start, end, AT);
    }

    static Result accept(IncomingSegment... segments) {
        return SegmentFilter.accept(Arrays.asList(segments), DURATION, Set.of());
    }

    static List<Range> ranges(Result result) {
        return result.accepted().stream().map(Accepted::range).toList();
    }

    @Test
    void 정상은_받는다() {
        Result result = accept(seg(0, 5000), seg(5000, 10_000));

        assertThat(ranges(result)).containsExactly(new Range(0, 5000), new Range(5000, 10_000));
        assertThat(result.accepted().get(0).recordedAt()).isEqualTo(AT);
        assertThat(result.dropped()).isZero();
    }

    @Test
    void 범위_밖은_dropped() {
        Result result = accept(seg(-1, 100), seg(100, 100), seg(200, 100), seg(0, DURATION + 1001));

        assertThat(result.accepted()).isEmpty();
        assertThat(result.dropped()).isEqualTo(4);
    }

    @Test
    void 끝은_길이_더하기_1초까지_받는다() {
        assertThat(ranges(accept(seg(9000, DURATION + 1000)))).containsExactly(new Range(9000, DURATION + 1000));
    }

    @Test
    void 길이_6만ms_초과는_dropped() {
        Result result = SegmentFilter.accept(List.of(seg(0, 60_000), seg(0, 60_001)), 100_000, Set.of());

        assertThat(ranges(result)).containsExactly(new Range(0, 60_000));
        assertThat(result.dropped()).isEqualTo(1);
    }

    @Test
    void 칸이_빠지면_dropped() {
        Result result = accept(seg(null, 100), seg(0, null), new IncomingSegment(0, 100, null), null);

        assertThat(result.accepted()).isEmpty();
        assertThat(result.dropped()).isEqualTo(4);
    }

    @Test
    void 쉰한_번째부터_dropped() {
        List<IncomingSegment> many = new ArrayList<>();
        for (int i = 0; i < 55; i++) {
            many.add(seg(i * 100, i * 100 + 50));
        }

        Result result = SegmentFilter.accept(many, DURATION, Set.of());

        assertThat(result.accepted()).hasSize(SegmentFilter.MAX_SEGMENTS).hasSize(50);
        assertThat(result.accepted().get(49).range()).isEqualTo(new Range(4900, 4950));
        assertThat(result.dropped()).isEqualTo(5);
    }

    @Test
    void 이미_받은_구간은_조용히_뺀다() {
        Result result =
                SegmentFilter.accept(List.of(seg(0, 5000), seg(5000, 6000)), DURATION, Set.of(new Range(0, 5000)));

        assertThat(ranges(result)).containsExactly(new Range(5000, 6000));
        assertThat(result.dropped()).isZero();
    }

    @Test
    void 같은_요청_안의_중복도_한_번() {
        Result result = accept(seg(0, 5000), seg(0, 5000));

        assertThat(ranges(result)).containsExactly(new Range(0, 5000));
        assertThat(result.dropped()).isZero();
    }
}
