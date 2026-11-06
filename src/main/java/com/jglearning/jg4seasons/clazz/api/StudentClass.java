package com.jglearning.jg4seasons.clazz.api;

/** 학생의 현재 반(StudentClasses.currentClass가 학원 범위로 확인한 값). */
public record StudentClass(long classId, long academyId, long bookId, boolean allowFutureLessons) {}
