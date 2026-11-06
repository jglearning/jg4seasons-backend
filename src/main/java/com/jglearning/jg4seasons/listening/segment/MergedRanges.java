package com.jglearning.jg4seasons.listening.segment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 청취 구간 병합(TRK-04, ERD track_progress 갱신 알고리즘). 기존 병합 구간과 새 구간을 합쳐 시작 순으로 정렬하고, 겹치거나 사이가 ADJACENT_MS 이하이면
 * 하나로 합친다. 끝은 트랙 길이로 자르고, 자른 뒤 남는 게 없으면 버린다. 순서·중복과 무관하게 같은 결과(TRK-06 멱등).
 */
public final class MergedRanges {

    /** 이 이하로 떨어진 구간은 이어 들은 것으로 본다(seek·flush 경계에서 생기는 작은 틈) */
    public static final int ADJACENT_MS = 200;

    private MergedRanges() {}

    /** 결과: 시작 순, 서로 ADJACENT_MS보다 멀리 떨어짐, 모두 [0, durationMs] 안 */
    public static List<Range> merge(List<Range> existing, List<Range> added, int durationMs) {
        List<Range> sorted = Stream.concat(existing.stream(), added.stream())
                .filter(range -> range.startMs() < durationMs)
                .sorted(Comparator.comparingInt(Range::startMs))
                .toList();
        List<Range> merged = new ArrayList<>();
        int start = -1;
        int end = -1;
        for (Range range : sorted) {
            int rangeEnd = Math.min(range.endMs(), durationMs);
            if (start >= 0 && range.startMs() - end <= ADJACENT_MS) {
                end = Math.max(end, rangeEnd);
                continue;
            }
            if (start >= 0) {
                merged.add(new Range(start, end));
            }
            start = range.startMs();
            end = rangeEnd;
        }
        if (start >= 0) {
            merged.add(new Range(start, end));
        }
        return List.copyOf(merged);
    }

    /** 청취율 = 덮은 길이 / 트랙 길이, 소수 4자리 내림(기준 도달을 반올림으로 앞당기지 않음), 1 상한. 길이가 0 이하면 데이터 오류 */
    public static BigDecimal coverage(int coveredMs, int durationMs) {
        if (durationMs <= 0) {
            throw new IllegalStateException("트랙 길이가 0 이하다: " + durationMs);
        }
        return BigDecimal.valueOf(coveredMs)
                .divide(BigDecimal.valueOf(durationMs), 4, RoundingMode.DOWN)
                .min(BigDecimal.ONE);
    }

    /** 병합된 구간 길이의 합(ms) */
    public static int coveredMs(List<Range> merged) {
        return merged.stream()
                .mapToInt(range -> range.endMs() - range.startMs())
                .sum();
    }
}
