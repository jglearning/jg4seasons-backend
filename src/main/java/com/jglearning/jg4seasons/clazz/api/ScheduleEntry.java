package com.jglearning.jg4seasons.clazz.api;

import java.time.LocalDate;
import java.util.Objects;

/**
 * 진도표 한 행(4.5). seq = 날짜 순 행 번호(편집마다 다시 매김 — ADR 0030), note는 없으면 null. status는 응답용 계산값(휴강 cancelled, 학원 날짜로 오늘 이전
 * done, 그 밖 scheduled — 이슈 #45)이라 DB 값과 다를 수 있다.
 */
public record ScheduleEntry(int seq, long lessonId, LocalDate scheduledDate, SessionStatus status, String note) {
    public ScheduleEntry {
        Objects.requireNonNull(scheduledDate, "scheduledDate");
        Objects.requireNonNull(status, "status");
    }
}
