package com.jglearning.jg4seasons.listening;

import java.util.Locale;

/** 회차 진도 상태(lesson_progress.status). 값은 소문자(not_started·in_progress·completed), DB·API 같음. */
public enum LessonProgressStatus {
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETED;

    /** DB·응답의 문자열 값(소문자, 로캘 무관) */
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** DB 값 → enum. DB ENUM이 막으므로 모르는 값은 데이터 오류(IllegalStateException, 기본값으로 바꾸지 않음) */
    public static LessonProgressStatus fromValue(String value) {
        for (LessonProgressStatus candidate : values()) {
            if (candidate.value().equals(value)) {
                return candidate;
            }
        }
        throw new IllegalStateException("모르는 회차 진도 상태: " + value);
    }
}
