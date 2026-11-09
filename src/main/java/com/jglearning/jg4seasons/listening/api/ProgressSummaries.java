package com.jglearning.jg4seasons.listening.api;

import java.time.Instant;
import java.util.Collection;

/**
 * 학원 현황(5.4)용 청취 집계 요약. 다른 모듈(admin)이 쓴다. 청취 집계에는 학원 칸이 없으므로 studentIds가 곧 학원 범위다 — 호출자는 그 학원 학생 id만
 * 넘긴다(clazz·auth api에서 받은 값). 빈 목록이면 0.
 */
public interface ProgressSummaries {

    /** lesson_progress.status = completed인 학생 수(studentIds 안에서) */
    int completedCount(long lessonId, Collection<Long> studentIds);

    /** track_progress.last_played_at ∈ [from, to)인 서로 다른 학생 수(studentIds 안에서) */
    int playedBetween(Collection<Long> studentIds, Instant from, Instant to);
}
