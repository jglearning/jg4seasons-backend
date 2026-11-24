package com.jglearning.jg4seasons.wordtest.grading;

import java.util.Locale;

/** 문항 판정. DB 값은 소문자 이름(V12 CHECK) */
public enum AnswerResult {
    CORRECT,
    WRONG,
    BLANK;

    public String dbValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** 소문자 DB 값만 받는다. 모르는 값은 IllegalArgumentException */
    public static AnswerResult fromDb(String value) {
        for (AnswerResult v : values()) {
            if (v.dbValue().equals(value)) {
                return v;
            }
        }
        throw new IllegalArgumentException("모르는 판정 결과: " + value);
    }
}
