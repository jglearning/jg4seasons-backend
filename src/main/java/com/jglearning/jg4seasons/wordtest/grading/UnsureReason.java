package com.jglearning.jg4seasons.wordtest.grading;

import java.util.Locale;

/** 읽기가 애매한 이유. 1단계는 UNREADABLE만 쓰고(판정은 unclear), 나머지는 2단계 학생 확인용. DB 값은 소문자 이름(V12 CHECK) */
public enum UnsureReason {
    /** 두 번 읽은 값이 다름(2단계) */
    READ_MISMATCH,
    /** 정답과 한 글자 차이(2단계) */
    NEAR_MISS,
    /** 읽을 수 없음 */
    UNREADABLE;

    public String dbValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** 소문자 DB 값만 받는다. 모르는 값은 IllegalArgumentException */
    public static UnsureReason fromDb(String value) {
        for (UnsureReason v : values()) {
            if (v.dbValue().equals(value)) {
                return v;
            }
        }
        throw new IllegalArgumentException("모르는 애매한 이유: " + value);
    }
}
