package com.jglearning.jg4seasons.listening.passage;

import com.jglearning.jg4seasons.content.api.LessonContent;
import com.jglearning.jg4seasons.listening.completion.CompletionRule;
import com.jglearning.jg4seasons.listening.completion.CompletionRuleEvaluator;
import com.jglearning.jg4seasons.listening.segment.MergedRanges;
import com.jglearning.jg4seasons.listening.segment.Range;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 한 지문을 여러 목소리로 들은 구간을 합친다(ADR 0034). 목소리별 병합 구간(track_progress.merged_ranges)을 지문의 기준 시간축으로 옮긴 뒤 구간 병합으로
 * 합집합을 만든다 — 같은 자리를 두 목소리로 들어도 한 번만 센다.
 *
 * <p>증분이 아니다: 그 지문의 필수 목소리 집계 전부에서 매번 다시 만든다. 지문 집계는 목소리별 집계에서 언제든 다시 만들 수 있는 값이다.
 */
public final class PassageCombiner {

    private PassageCombiner() {}

    /**
     * 지문의 기준 목소리: 규칙의 필수 목소리 중 트랙 순서가 가장 앞인 것. 목소리 이름을 코드에 적지 않는다. 필수 목소리 트랙이 하나도 없는 지문(음원이 아직 없거나 규칙의
     * 필수 목소리를 좁힌 경우)은 기준이 없다 — 그 지문은 합치지 않고 지금까지처럼 "완료될 수 없는 지문"으로 남는다(오류가 아니다).
     */
    public static Optional<LessonContent.Voice> reference(LessonContent.Passage passage, CompletionRule rule) {
        return passage.voices().stream()
                .filter(voice -> CompletionRuleEvaluator.isRequired(rule, voice.voice()))
                .findFirst();
    }

    /**
     * 필수 목소리 트랙들의 병합 구간을 기준 시간축으로 옮겨 합친다.
     *
     * @param reference 이 지문의 기준 목소리({@link #reference}) — 저장할 행을 잠글 때 쓴 기준과 같은 값을 넘긴다
     * @param mergedByTrackId 트랙 id → 그 트랙의 병합 구간. 없는 트랙은 안 들은 것. 필수가 아닌 목소리의 구간은 넣어도 쓰지 않는다
     */
    public static CombinedProgress combine(
            LessonContent.Passage passage,
            CompletionRule rule,
            LessonContent.Voice reference,
            Map<Long, List<Range>> mergedByTrackId) {
        VoiceAxis referenceAxis = VoiceAxis.of(reference);
        List<Range> merged = List.of();
        for (LessonContent.Voice voice : passage.voices()) {
            List<Range> heard = mergedByTrackId.getOrDefault(voice.trackId(), List.of());
            if (heard.isEmpty() || !CompletionRuleEvaluator.isRequired(rule, voice.voice())) {
                continue;
            }
            List<Range> onReference = voice.trackId() == reference.trackId()
                    ? heard
                    : PassageAxis.mapAll(VoiceAxis.of(voice), referenceAxis, heard);
            merged = MergedRanges.merge(merged, onReference, reference.durationMs());
        }
        return CombinedProgress.of(reference, merged);
    }
}
