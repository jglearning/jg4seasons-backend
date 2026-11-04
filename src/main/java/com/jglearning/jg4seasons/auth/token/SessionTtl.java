package com.jglearning.jg4seasons.auth.token;

import com.jglearning.jg4seasons.auth.UserRole;
import java.time.Duration;

/** 세션 수명(API 명세 §0.1): access 30분, refresh 학생 90일 · 교사·관리자 14일. */
public final class SessionTtl {

    public static final Duration ACCESS = Duration.ofMinutes(30);
    public static final Duration STUDENT_REFRESH = Duration.ofDays(90);
    public static final Duration STAFF_REFRESH = Duration.ofDays(14);

    private SessionTtl() {}

    /** 역할이 늘면 컴파일러가 여기를 짚는다(default 없음) */
    public static Duration refreshFor(UserRole role) {
        return switch (role) {
            case STUDENT -> STUDENT_REFRESH;
            case TEACHER, ADMIN, SUPERADMIN -> STAFF_REFRESH;
        };
    }
}
