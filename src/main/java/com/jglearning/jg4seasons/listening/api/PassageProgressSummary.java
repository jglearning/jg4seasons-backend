package com.jglearning.jg4seasons.listening.api;

import java.math.BigDecimal;

/**
 * 학생 한 명의 지문 값(ADR 0034 결정 5·7) — 목소리를 섞어 들은 것까지 합친, 화면에 그대로 나가는 값이다. coverage 0~1 = 합친 청취율과 필수 목소리 하나의 최댓값 중 큰
 * 쪽. completed = 필수 목소리 하나를 기준 이상 들었거나 합친 청취율이 기준 이상(한 번 되면 유지). 범위를 벗어난 청취율은 데이터 오류(IllegalStateException →
 * 500).
 */
public record PassageProgressSummary(BigDecimal coverage, boolean completed) {
    public PassageProgressSummary {
        if (coverage == null || coverage.signum() < 0 || coverage.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalStateException("지문 청취율은 0~1이어야 한다: " + coverage);
        }
    }
}
