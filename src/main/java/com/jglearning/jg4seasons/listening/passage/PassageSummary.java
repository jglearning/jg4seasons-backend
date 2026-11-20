package com.jglearning.jg4seasons.listening.passage;

import java.math.BigDecimal;

/** 화면·응답에 나가는 지문 값(바닥 규칙 적용 끝): 지문 청취율 0~1과 지문 완료 */
public record PassageSummary(BigDecimal coverage, boolean completed) {}
