package com.jglearning.jg4seasons.clazz.api;

import java.util.List;
import java.util.Set;

/**
 * 교사 화면(4.1·4.2)이 보는 반. lessons는 StudentClasses.lessons와 같은 접기(회차마다 하나, seq 순). doneLessonIds는 대표 행(ClassLesson.seq의
 * 행)이 휴강이 아니고 수업 날짜가 오늘(학원 날짜) 이전인 회차 — 저장값이 아니라 날짜로(진도표 4.5와 같은 판정, 이슈 #45). studentIds는 현재 소속(left_at
 * 없음), id 순. accessCode는 반 가입 코드(반 공용 8자) — 교사 화면의 입장 링크·QR용.
 */
public record TeacherClass(
        long classId,
        String name,
        String accessCode,
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
