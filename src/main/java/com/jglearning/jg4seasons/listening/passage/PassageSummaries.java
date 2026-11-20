package com.jglearning.jg4seasons.listening.passage;

import com.jglearning.jg4seasons.content.api.LessonContent;
import com.jglearning.jg4seasons.listening.completion.CompletionRule;
import com.jglearning.jg4seasons.listening.completion.CompletionRuleEvaluator;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

/**
 * 화면·응답에 나가는 지문 값을 만든다(ADR 0034 결정 5·7). 학생 회차 상세와 flush 응답이 이 계산을 탄다 — 화면마다 지문 완료가 달라지지 않게. 교사 화면의 지문 요약도
 * 여기를 거쳐야 한다(후속).
 *
 * <p>바닥 규칙: 지문 집계(passage_progress) 행이 없거나(표가 생기기 전 기록, 합산이 꺼진 규칙) 기준이 달라 쓸 수 없어도, 필수 목소리 트랙의 완료와 청취율로 값이
 * 나간다. 지금까지 완료였던 지문이 미완료가 되지 않는다.
 */
public final class PassageSummaries {

    private PassageSummaries() {}

    /**
     * 이 지문의 필수 목소리 트랙 중 완료한 것이 있는가 — 지금까지의 지문 완료 규칙(ADR 0027)이고 합산과 상관없이 늘 인정한다. flush의 완료 판정과 화면 값이 같은 술어를
     * 쓴다.
     *
     * @param tracks 트랙 id → 집계. 없는 트랙은 안 들은 것. 다른 지문의 트랙이 섞여 있어도 이 지문의 필수 목소리만 본다
     */
    public static boolean requiredTrackCompleted(
            LessonContent.Passage passage, CompletionRule rule, Map<Long, TrackState> tracks) {
        return passage.voices().stream()
                .filter(voice -> CompletionRuleEvaluator.isRequired(rule, voice.voice()))
                .map(voice -> tracks.get(voice.trackId()))
                .anyMatch(track -> track != null && track.completed());
    }

    /**
     * completed = 저장 행이 완료(쓸 수 없는 행이어도 — 완료는 한 번 되면 유지) 또는 필수 목소리 트랙 중 완료한 것이 있음. coverage = max(쓸 수 있는 저장 행의
     * 합친 청취율, 필수 목소리 트랙 청취율의 최댓값) — 쓸 수 있는 집계가 있는 한 완료인데 기준보다 낮은 숫자가 나가지 않는다. 아무것도 없으면 0.
     *
     * @param tracks 트랙 id → 집계. 없는 트랙은 안 들은 것. 필수가 아닌 목소리는 보지 않는다
     */
    public static PassageSummary of(
            LessonContent.Passage passage,
            CompletionRule rule,
            Optional<StoredPassage> stored,
            Map<Long, TrackState> tracks) {
        BigDecimal coverage = BigDecimal.ZERO;
        for (LessonContent.Voice voice : passage.voices()) {
            TrackState track = tracks.get(voice.trackId());
            if (track != null && CompletionRuleEvaluator.isRequired(rule, voice.voice())) {
                coverage = coverage.max(track.coverage());
            }
        }
        Optional<StoredPassage.OnReference> combined =
                stored.flatMap(row -> row.on(PassageCombiner.reference(passage, rule)));
        if (combined.isPresent()) {
            coverage = coverage.max(combined.get().coverage());
        }
        boolean storedCompleted = stored.map(StoredPassage::completed).orElse(false);
        return new PassageSummary(coverage, storedCompleted || requiredTrackCompleted(passage, rule, tracks));
    }
}
