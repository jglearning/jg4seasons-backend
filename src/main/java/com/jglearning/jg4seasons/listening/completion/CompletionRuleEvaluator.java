package com.jglearning.jg4seasons.listening.completion;

import java.math.BigDecimal;
import java.util.Set;

/** 완료 규칙 해석기(ADR-07). 임계값·필수 트랙은 규칙(데이터)에서만 온다 — 코드에 기준 값을 두지 않는다. */
public final class CompletionRuleEvaluator {

    private CompletionRuleEvaluator() {}

    /** 트랙 청취율(0~1)이 규칙의 기준 이상이면 그 트랙 완료 */
    public static boolean isTrackComplete(CompletionRule rule, BigDecimal coverage) {
        return coverage.compareTo(rule.threshold()) >= 0;
    }

    /** 완료한 속도 중 규칙의 필수 속도가 하나라도 있으면 그 지문 완료 */
    public static boolean isPassageComplete(CompletionRule rule, Set<String> completedSpeeds) {
        return completedSpeeds.stream().anyMatch(rule.requiredSpeeds()::contains);
    }

    /** 이 속도가 지문 완료에 필요한 속도인가(회차 상세의 required) */
    public static boolean isRequired(CompletionRule rule, String speed) {
        return rule.requiredSpeeds().contains(speed);
    }
}
