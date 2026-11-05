package com.jglearning.jg4seasons.clazz.api;

/** 학생 반 기준 회차 접근. */
public enum LessonAccess {
    /** 열림(수업일 당일부터, 또는 미래 회차 허용) */
    OPEN,
    /** 진도표에 있지만 아직 열리지 않음(미래 회차, 휴강만 있음) */
    LOCKED,
    /** 그 반 진도표에 없는 회차(다른 권 포함) */
    NOT_IN_CLASS
}
