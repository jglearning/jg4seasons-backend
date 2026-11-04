package com.jglearning.jg4seasons.auth.token;

import java.time.Duration;

/** 세션 수명(API 명세 §0.1): access 30분, refresh 학생 90일 · 교사·관리자 14일. */
public final class SessionTtl {

    public static final Duration ACCESS = Duration.ofMinutes(30);
    public static final Duration STUDENT_REFRESH = Duration.ofDays(90);
    public static final Duration STAFF_REFRESH = Duration.ofDays(14);

    private SessionTtl() {}

    /** @param role user.role 값(소문자) */
    public static Duration refreshFor(String role) {
        return "student".equals(role) ? STUDENT_REFRESH : STAFF_REFRESH;
    }
}
