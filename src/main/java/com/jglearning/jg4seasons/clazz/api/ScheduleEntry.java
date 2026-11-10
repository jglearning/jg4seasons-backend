package com.jglearning.jg4seasons.clazz.api;

import java.time.LocalDate;
import java.util.Objects;

/** 진도표 한 행(4.5). seq = 날짜 순 행 번호(편집마다 다시 매김 — ADR 0030), note는 없으면 null. */
public record ScheduleEntry(int seq, long lessonId, LocalDate scheduledDate, SessionStatus status, String note) {
    public ScheduleEntry {
        Objects.requireNonNull(scheduledDate, "scheduledDate");
        Objects.requireNonNull(status, "status");
    }
}
