package com.jglearning.jg4seasons.clazz.api;

import java.util.List;
import java.util.Optional;

/** 학원 현황용 진행 중 반: 오늘 회차(TodayLesson 규칙, 개강 전이면 빈 값)와 현재 소속 학생 id(id 순). */
public record ActiveClassToday(
        long classId, String name, long bookId, Optional<ClassLesson> today, List<Long> studentIds) {

    public ActiveClassToday {
        studentIds = List.copyOf(studentIds);
    }
}
