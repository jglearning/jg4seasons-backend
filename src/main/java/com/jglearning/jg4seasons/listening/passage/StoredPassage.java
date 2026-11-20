package com.jglearning.jg4seasons.listening.passage;

import com.jglearning.jg4seasons.content.api.LessonContent;
import com.jglearning.jg4seasons.listening.segment.Range;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 저장된 지문 집계 한 행(passage_progress). 완료(completed)는 언제나 믿는다 — 한 번 되면 유지한다. 합친 구간과 청취율은 계산할 때의 기준 시간축 위의 값이라, 지금
 * 기준 목소리를 넘겨 {@link #on}으로 풀어야만 꺼낼 수 있다. 콘텐츠를 다시 넣어 기준 트랙의 길이가 바뀌었거나 규칙이 바뀌어 기준 목소리가 달라졌으면 옛 시간축의 값이라 나오지
 * 않는다.
 *
 * <p>레코드가 아닌 이유: 구간·청취율을 기준 확인 없이 읽는 접근자를 두지 않으려고. 범위를 벗어난 값은 데이터 오류(IllegalStateException → 500) — 화면에 그대로
 * 나가지 않게 여기서 막는다.
 */
public final class StoredPassage {

    private final long refTrackId;
    private final int refDurationMs;
    private final List<Range> mergedRanges;
    private final BigDecimal coverage;
    private final boolean completed;

    public StoredPassage(
            long refTrackId, int refDurationMs, List<Range> mergedRanges, BigDecimal coverage, boolean completed) {
        if (refDurationMs <= 0) {
            throw new IllegalStateException("지문 집계의 기준 길이는 0보다 커야 한다: " + refDurationMs);
        }
        if (coverage == null || coverage.signum() < 0 || coverage.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalStateException("지문 청취율은 0~1이어야 한다: " + coverage);
        }
        this.refTrackId = refTrackId;
        this.refDurationMs = refDurationMs;
        this.mergedRanges = List.copyOf(mergedRanges);
        this.coverage = coverage;
        this.completed = completed;
    }

    public boolean completed() {
        return completed;
    }

    /** 계산할 때의 기준 트랙 — 로그·진단용 */
    public long refTrackId() {
        return refTrackId;
    }

    /** 계산할 때의 기준 트랙 길이 — 로그·진단용 */
    public int refDurationMs() {
        return refDurationMs;
    }

    /** 계산할 때의 기준(트랙·길이)이 지금 기준 목소리와 같을 때만 합친 구간과 청취율이 나온다 */
    public Optional<OnReference> on(LessonContent.Voice reference) {
        return refTrackId == reference.trackId() && refDurationMs == reference.durationMs()
                ? Optional.of(new OnReference(reference, mergedRanges, coverage))
                : Optional.empty();
    }

    /** 기준 시간축 위의 합친 구간과 청취율 — 어느 목소리의 시간축인지를 함께 든다 */
    public record OnReference(LessonContent.Voice reference, List<Range> ranges, BigDecimal coverage) {

        public OnReference {
            ranges = List.copyOf(ranges);
        }

        /** 합친 구간을 이 목소리의 시간축으로 옮긴 것. 기준 목소리 자신이면 그대로 */
        public List<Range> rangesOn(LessonContent.Voice voice) {
            return voice.trackId() == reference.trackId()
                    ? ranges
                    : PassageAxis.mapAll(VoiceAxis.of(reference), VoiceAxis.of(voice), ranges);
        }
    }
}
