package com.jglearning.jg4seasons.clazz.api;

import java.time.LocalDate;

/**
 * 진도표 한 행 변경(4.6). scheduledDate·note는 null이면 그대로, note ""면 지움. shiftFollowing이면 뒤 수업 행들을 새 날짜의 다음 수업일부터 차례로(ADR
 * 0030).
 */
public record ScheduleChange(LocalDate scheduledDate, String note, boolean shiftFollowing) {}
