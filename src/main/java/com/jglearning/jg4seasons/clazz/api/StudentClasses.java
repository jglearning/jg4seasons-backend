package com.jglearning.jg4seasons.clazz.api;

import java.time.LocalDate;
import java.util.Optional;

/** 학생 화면이 쓰는 반 조회: 현재 반, 회차 열림(STU-03). */
public interface StudentClasses {

    /** 현재 소속(left_at 없음)인 active 반 중 id가 가장 작은 것. 학원이 다르면 empty */
    Optional<StudentClass> currentClass(long academyId, long studentId);

    /**
     * 그 반 진도표 기준 회차 접근. today는 학원 타임존 날짜. clazz는 currentClass가 돌려준 값만 넘긴다(학원 범위 확인이 끝난 반 — class_session에는
     * academy_id가 없다)
     */
    LessonAccess access(StudentClass clazz, long lessonId, LocalDate today);
}
