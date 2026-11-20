package com.jglearning.jg4seasons.listening.passage;

import com.jglearning.jg4seasons.listening.segment.Range;
import java.math.BigDecimal;
import java.util.List;

/**
 * 한 지문을 여러 목소리로 들은 구간을 기준 시간축에서 합친 결과(passage_progress에 저장하는 값). mergedRanges·coveredMs는 기준 트랙(refTrackId)의 ms이고
 * coverage = coveredMs / refDurationMs(0~1).
 */
public record CombinedProgress(
        long refTrackId, int refDurationMs, List<Range> mergedRanges, int coveredMs, BigDecimal coverage) {

    public CombinedProgress {
        mergedRanges = List.copyOf(mergedRanges);
    }
}
