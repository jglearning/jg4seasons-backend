package com.jglearning.jg4seasons.clazz.api;

import java.util.Locale;

/** 반 상태(class.status). 값은 소문자(active·finished·archived), DB·API 같음. active인 반만 학생이 입장한다. */
public enum ClassStatus {
    ACTIVE,
    FINISHED,
    ARCHIVED;

    /** DB·요청·응답의 문자열 값(소문자, 로캘 무관) */
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** 소문자 값 → enum. 모르는 값이나 대소문자가 다른 값은 IllegalArgumentException(기본값으로 바꾸지 않음) */
    public static ClassStatus fromValue(String value) {
        for (ClassStatus candidate : values()) {
            if (candidate.value().equals(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("모르는 값: " + value);
    }
}
