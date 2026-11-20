package com.jglearning.jg4seasons.listening.passage;

import java.math.BigDecimal;

/** 목소리 트랙 하나의 집계 중 지문 판정에 쓰는 값(track_progress): 청취율 0~1과 완료 여부 */
public record TrackState(BigDecimal coverage, boolean completed) {}
