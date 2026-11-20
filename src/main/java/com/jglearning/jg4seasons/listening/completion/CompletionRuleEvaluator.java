package com.jglearning.jg4seasons.listening.completion;

import java.math.BigDecimal;

/** 완료 규칙 해석기(ADR-07). 임계값·필수 트랙은 규칙(데이터)에서만 온다 — 코드에 기준 값을 두지 않는다. */
public final class CompletionRuleEvaluator {

    private CompletionRuleEvaluator() {}

    /** 트랙 청취율(0~1)이 규칙의 기준 이상이면 그 트랙 완료 */
    public static boolean isTrackComplete(CompletionRule rule, BigDecimal coverage) {
        return coverage.compareTo(rule.threshold()) >= 0;
    }

    /**
     * 지문 완료(ADR-27·34). 필수 목소리 트랙 중 완료한 것이 있으면 완료 — 합산과 상관없이 늘 인정한다(바닥 규칙). 합산이 켜져 있으면 필수 목소리로 들은 구간을 합친
     * 청취율(0~1)이 규칙의 기준 이상이어도 완료.
     *
     * @param requiredTrackCompleted 이 지문의 필수 목소리 트랙 중 완료한 것이 있는가
     * @param combinedCoverage 필수 목소리로 들은 구간을 지문의 기준 시간축에서 합친 청취율(0~1)
     */
    public static boolean isPassageComplete(
            CompletionRule rule, boolean requiredTrackCompleted, BigDecimal combinedCoverage) {
        return requiredTrackCompleted || (rule.combineVoices() && combinedCoverage.compareTo(rule.threshold()) >= 0);
    }

    /** 이 목소리가 지문 완료에 들어가는 목소리인가(회차 상세의 required) */
    public static boolean isRequired(CompletionRule rule, String voice) {
        return rule.requiredVoices().contains(voice);
    }
}
