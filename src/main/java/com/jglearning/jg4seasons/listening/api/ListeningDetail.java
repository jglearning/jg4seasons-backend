package com.jglearning.jg4seasons.listening.api;

import java.util.Map;

/**
 * 학생 묶음 × 회차들의 청취 집계 한 번 읽기. 지문 요약은 이 트랙 집계로 계산한 것이라 둘이 서로 어긋나지 않는다.
 *
 * @param tracks 학생 id → 트랙 id → 집계. 집계 행이 있는 것만(없으면 안 들음)
 * @param passages 학생 id → 지문 id(LessonContent.Passage.id) → 요약. 묶음의 모든 학생 × 넘긴 회차의 모든 지문이 들어 있다(안 들은 지문은 0·미완료)
 */
public record ListeningDetail(
        Map<Long, Map<Long, TrackProgressSummary>> tracks, Map<Long, Map<Long, PassageProgressSummary>> passages) {}
