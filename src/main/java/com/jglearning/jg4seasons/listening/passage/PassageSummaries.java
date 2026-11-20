package com.jglearning.jg4seasons.listening.passage;

import com.jglearning.jg4seasons.content.api.LessonContent;
import com.jglearning.jg4seasons.listening.completion.CompletionRule;
import com.jglearning.jg4seasons.listening.completion.CompletionRuleEvaluator;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

/**
 * 화면·응답에 나가는 지문 값을 만든다(ADR 0034 결정 5·7). 학생 회차 상세·flush 응답·교사 화면이 모두 이 계산을 탄다 — 화면마다 지문 완료가 달라지지 않게.
 *
 * <p>바닥 규칙: 지문 집계(passage_progress) 행이 없거나(표가 생기기 전 기록, 합산이 꺼진 규칙) 기준이 달라 쓸 수 없어도, 필수 목소리 트랙의 완료와 청취율로 값이
 * 나간다. 지금까지 완료였던 지문이 미완료가 되지 않는다.
 */
public final class PassageSummaries {

    private PassageSummaries() {}

    /**
     * 저장 행의 구간·청취율을 지금 쓸 수 있는가: 계산할 때의 기준 트랙·기준 길이가 지금 기준 목소리와 같아야 한다. 콘텐츠를 다시 넣어 기준 트랙의 길이가 바뀌었거나 규칙의
     * 필수 목소리가 바뀌어 기준 목소리가 달라졌으면 옛 시간축의 값이다.
     */
    public static boolean usable(StoredPassage stored, LessonContent.Voice reference) {
        return stored.refTrackId() == reference.trackId() && stored.refDurationMs() == reference.durationMs();
    }

    /**
     * completed = 저장 행이 완료(쓸 수 없는 행이어도 — 완료는 한 번 되면 유지) 또는 필수 목소리 트랙 중 완료한 것이 있음. coverage = max(쓸 수 있는 저장 행의
     * 합친 청취율, 필수 목소리 트랙 청취율의 최댓값) — 완료인데 기준보다 낮은 숫자가 나가지 않게. 아무것도 없으면 0.
     *
     * @param tracks 트랙 id → 집계. 없는 트랙은 안 들은 것. 필수가 아닌 목소리는 보지 않는다
     */
    public static PassageSummary of(
            LessonContent.Passage passage,
            CompletionRule rule,
            Optional<StoredPassage> stored,
            Map<Long, TrackState> tracks) {
        BigDecimal coverage = BigDecimal.ZERO;
        boolean trackCompleted = false;
        for (LessonContent.Voice voice : passage.voices()) {
            TrackState track = tracks.get(voice.trackId());
            if (track == null || !CompletionRuleEvaluator.isRequired(rule, voice.voice())) {
                continue;
            }
            coverage = coverage.max(track.coverage());
            trackCompleted |= track.completed();
        }
        boolean storedCompleted = stored.map(StoredPassage::completed).orElse(false);
        if (stored.isPresent() && usable(stored.get(), PassageCombiner.reference(passage, rule))) {
            coverage = coverage.max(stored.get().coverage());
        }
        return new PassageSummary(coverage, storedCompleted || trackCompleted);
    }
}
