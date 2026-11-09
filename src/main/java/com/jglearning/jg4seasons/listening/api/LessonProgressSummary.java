package com.jglearning.jg4seasons.listening.api;

import java.time.Instant;
import java.util.Objects;

/** 학생 한 명의 회차 진도(lesson_progress). required* = 필수 트랙 완료·전체 수. completedAt은 completed일 때만(UTC), 아니면 null. */
public record LessonProgressSummary(
        LessonProgressStatus status, int requiredDone, int requiredTotal, Instant completedAt) {

    public LessonProgressSummary {
        Objects.requireNonNull(status, "status");
        if (status != LessonProgressStatus.COMPLETED) {
            completedAt = null;
        }
    }
}
