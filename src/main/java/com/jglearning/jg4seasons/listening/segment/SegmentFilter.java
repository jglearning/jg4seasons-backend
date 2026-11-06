package com.jglearning.jg4seasons.listening.segment;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * flush로 받은 세그먼트 검증(03 §3.1). 요청 순서대로 앞 MAX_SEGMENTS개만 보고 나머지는 버린다. 버린 것은 dropped로 세고 요청은 200으로 끝난다(클라이언트가
 * 다시 보내도 같은 결과라 재전송을 유도하지 않음). 같은 세션에서 이미 받은 구간과 같은 요청 안의 중복은 정상 재전송이라 dropped로 세지 않고 뺀다(결정 C —
 * 총 재생 시간이 재전송으로 부풀지 않게).
 */
public final class SegmentFilter {

    public static final int MAX_SEGMENTS = 50;
    public static final int MAX_LENGTH_MS = 60_000;
    /** 끝이 트랙 길이를 이만큼까지 넘는 것은 받는다(브라우저 currentTime 오차). 병합 때 길이로 자른다 */
    public static final int DURATION_TOLERANCE_MS = 1000;

    /** 요청 값 그대로(빠진 칸은 null) */
    public record IncomingSegment(Integer startMs, Integer endMs, Instant recordedAt) {}

    public record Accepted(Range range, Instant recordedAt) {}

    public record Result(List<Accepted> accepted, int dropped) {
        public Result {
            accepted = List.copyOf(accepted);
        }
    }

    private SegmentFilter() {}

    public static Result accept(List<IncomingSegment> incoming, int durationMs, Set<Range> alreadyReceived) {
        List<Accepted> accepted = new ArrayList<>();
        Set<Range> seen = new HashSet<>(alreadyReceived);
        int dropped = Math.max(0, incoming.size() - MAX_SEGMENTS);
        for (IncomingSegment segment : incoming.subList(0, Math.min(incoming.size(), MAX_SEGMENTS))) {
            if (!isAcceptable(segment, durationMs)) {
                dropped++;
                continue;
            }
            Range range = new Range(segment.startMs(), segment.endMs());
            if (seen.add(range)) {
                accepted.add(new Accepted(range, segment.recordedAt()));
            }
        }
        return new Result(accepted, dropped);
    }

    private static boolean isAcceptable(IncomingSegment segment, int durationMs) {
        return segment != null
                && segment.startMs() != null
                && segment.endMs() != null
                && segment.recordedAt() != null
                && Range.isValid(segment.startMs(), segment.endMs())
                && segment.endMs() <= durationMs + DURATION_TOLERANCE_MS
                && segment.endMs() - segment.startMs() <= MAX_LENGTH_MS;
    }
}
