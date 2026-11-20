package com.jglearning.jg4seasons.listening.passage;

import com.jglearning.jg4seasons.listening.segment.Range;
import java.math.BigDecimal;
import java.util.List;

/**
 * 저장된 지문 집계 한 행(passage_progress). refTrackId·refDurationMs는 계산할 때의 기준 — 지금 기준과 다르면 mergedRanges·coverage는 쓰지
 * 않는다(PassageSummaries.usable). completed는 그래도 믿는다(완료는 한 번 되면 유지).
 */
public record StoredPassage(
        long refTrackId, int refDurationMs, List<Range> mergedRanges, BigDecimal coverage, boolean completed) {

    public StoredPassage {
        mergedRanges = List.copyOf(mergedRanges);
    }
}
