package com.jglearning.jg4seasons.admin;

/** 명단 가져오기 경고 종류(ADR 0036). 생성을 막지 않는다 — 오류 code(ErrorCode)가 아니다. 확인은 화면이 받는다. */
public enum RosterWarning {
    /** 학원에 이름이 같은 활성 학생이 이미 있음(같은 명단 두 번 붙여넣기·동명이인) */
    SAME_NAME_STUDENT,
    /** 새 반 이름이 학원의 진행 중 반 이름과 같음 */
    SAME_NAME_CLASS
}
