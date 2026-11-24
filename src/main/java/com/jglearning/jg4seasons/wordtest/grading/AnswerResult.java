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
}
