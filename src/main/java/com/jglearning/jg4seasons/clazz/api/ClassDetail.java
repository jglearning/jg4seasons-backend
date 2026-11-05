package com.jglearning.jg4seasons.clazz.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** 관리자용 반 정보. weekdays는 1(월)~7(일) 오름차순, studentCount는 현재 소속(left_at 없음) 학생 수. */
public record ClassDetail(
        long id,
        String name,
        long bookId,
        LocalDate startDate,
        List<Integer> weekdays,
        String accessCode,
        Instant codeIssuedAt,
        boolean allowFutureLessons,
        ClassStatus status,
        int studentCount) {

    public ClassDetail {
        weekdays = List.copyOf(weekdays);
    }
}
