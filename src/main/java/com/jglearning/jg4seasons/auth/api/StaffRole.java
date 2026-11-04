package com.jglearning.jg4seasons.auth.api;

import java.util.Locale;

/** 교사 계정 역할(관리자 API에서 만들 수 있는 것만). 값은 소문자(teacher·admin). */
public enum StaffRole {
    TEACHER,
    ADMIN;

    /** 요청·응답의 문자열 값(소문자) */
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** 소문자 값 → enum. 모르는 값이나 대소문자가 다른 값은 IllegalArgumentException(기본값으로 바꾸지 않음) */
    public static StaffRole fromValue(String value) {
        for (StaffRole candidate : values()) {
            if (candidate.value().equals(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("모르는 값: " + value);
    }
}
