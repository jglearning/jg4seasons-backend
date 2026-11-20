package com.jglearning.jg4seasons.listening.passage;

import java.math.BigDecimal;

/**
 * 화면·응답에 나가는 지문 값(바닥 규칙 적용 끝): 지문 청취율 0~1과 지문 완료. 범위를 벗어난 청취율은 데이터 오류(IllegalStateException → 500) — 어느 경로로
 * 만들어졌든 화면에 그대로 나가지 않게 마지막 값에서 막는다.
 */
public record PassageSummary(BigDecimal coverage, boolean completed) {

    public PassageSummary {
        if (coverage == null || coverage.signum() < 0 || coverage.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalStateException("지문 청취율은 0~1이어야 한다: " + coverage);
        }
    }
}
