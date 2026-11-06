package com.jglearning.jg4seasons.clazz.api;

import java.time.LocalDate;

/**
 * 반 진도표의 회차 하나(휴강 시프트로 행이 여럿이면 접은 값). seq·scheduledDate는 휴강이 아닌 행 중 seq가 가장 작은 것(없으면 휴강 행). reached = 휴강이
 * 아닌 행의 수업일이 오늘(학원 타임존) 이하 — "오늘 회차"·밀린 수업은 이것으로 고른다. open = 학생이 들을 수 있음(미래 회차 허용 반은 reached가 아니어도 open).
 */
public record ClassLesson(long lessonId, int seq, LocalDate scheduledDate, boolean reached, boolean open) {}
