package com.jglearning.jg4seasons.clazz.api;

import java.util.List;
import java.util.Set;

/**
 * 교사 화면(4.1·4.2)이 보는 반. lessons는 StudentClasses.lessons와 같은 접기(회차마다 하나, seq 순). doneLessonIds는 대표 행(ClassLesson.seq의
 * 행)의 상태가 done인 회차. studentIds는 현재 소속(left_at 없음), id 순.
 */
public record TeacherClass(
        long classId,
        String name,
        long bookId,
        ClassStatus status,
        List<ClassLesson> lessons,
        Set<Long> doneLessonIds,
        List<Long> studentIds) {

    public TeacherClass {
        lessons = List.copyOf(lessons);
        doneLessonIds = Set.copyOf(doneLessonIds);
        studentIds = List.copyOf(studentIds);
    }
}
