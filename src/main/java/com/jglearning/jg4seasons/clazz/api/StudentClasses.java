package com.jglearning.jg4seasons.clazz.api;

import java.time.LocalDate;
import java.util.List;

/** 학생 화면이 쓰는 반 조회: 현재 반, 회차 열림(STU-03). */
public interface StudentClasses {

    /** 현재 소속(left_at 없음)인 active 반 전부, id 순(홈은 첫 번째 기준). 학원 범위로 거른다 — 다른 학원 반은 빠진다 */
    List<StudentClass> currentClasses(long academyId, long studentId);

    /** 그 반 진도표를 회차마다 하나로 접어 seq 순으로. today는 학원 타임존 날짜. clazz는 currentClasses가 돌려준 값만 */
    List<ClassLesson> lessons(StudentClass clazz, LocalDate today);

    /**
     * 그 반 진도표 기준 회차 접근. today는 학원 타임존 날짜. clazz는 currentClasses가 돌려준 값만 넘긴다(학원 범위 확인이 끝난 반 — class_session에는
     * academy_id가 없다)
     */
    LessonAccess access(StudentClass clazz, long lessonId, LocalDate today);

    /**
     * 여러 반 기준 회차 접근(학생이 두 반이면 어느 반에서든 들을 수 있다 — 2.2가 모든 소속 반을 보여 주므로). 한 반이라도 열렸으면 OPEN, 진도표에는 있지만
     * 모두 잠겼으면 LOCKED, 어느 반 진도표에도 없으면(빈 목록 포함) NOT_IN_CLASS. classes는 currentClasses가 돌려준 값만
     */
    LessonAccess access(List<StudentClass> classes, long lessonId, LocalDate today);
}
