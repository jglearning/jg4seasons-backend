package com.jglearning.jg4seasons.listening.passage;

import com.jglearning.jg4seasons.content.api.LessonContent;
import com.jglearning.jg4seasons.listening.segment.MergedRanges;
import com.jglearning.jg4seasons.listening.segment.Range;
import java.math.BigDecimal;
import java.util.List;

/**
 * 한 지문을 여러 목소리로 들은 구간을 기준 시간축에서 합친 결과(passage_progress에 저장하는 값). mergedRanges·coveredMs는 기준 트랙(refTrackId)의 ms이고
 * coverage = coveredMs / refDurationMs(0~1). 덮은 길이와 청취율은 구간에서 나오는 값이라 {@link #of}로 만든다.
 */
public record CombinedProgress(
        long refTrackId, int refDurationMs, List<Range> mergedRanges, int coveredMs, BigDecimal coverage) {

    public CombinedProgress {
        mergedRanges = List.copyOf(mergedRanges);
        if (refDurationMs <= 0 || coveredMs < 0 || coveredMs > refDurationMs) {
            throw new IllegalStateException("덮은 길이는 0~기준 길이여야 한다: " + coveredMs + " / " + refDurationMs);
        }
    }

    /** 기준 목소리의 시간축에서 병합이 끝난 구간으로 만든다 */
    public static CombinedProgress of(LessonContent.Voice reference, List<Range> merged) {
        int coveredMs = MergedRanges.coveredMs(merged);
        return new CombinedProgress(
                reference.trackId(),
                reference.durationMs(),
                merged,
                coveredMs,
                MergedRanges.coverage(coveredMs, reference.durationMs()));
    }

    /** 저장했을 때 읽히는 행과 같은 값(방금 계산한 것을 응답에 쓸 때) */
    public StoredPassage stored(boolean completed) {
        return new StoredPassage(refTrackId, refDurationMs, mergedRanges, coverage, completed);
    }
}
