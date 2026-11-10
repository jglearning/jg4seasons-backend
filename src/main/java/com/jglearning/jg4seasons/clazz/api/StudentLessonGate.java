package com.jglearning.jg4seasons.clazz.api;

/**
 * 학생이 회차를 열 수 있는지 한 곳에서 확인한다(2.3 회차 상세·3.1 flush·2.4 단어·2.5 학습 결과). 소속 반 중 그 회차 권의 반 전부를 보고, 하나라도 열렸으면 통과(학생이 두 반이면
 * 어느 반에서든). 학원이 활성인지·회차가 있는지는 호출자가 먼저 확인한다.
 */
public interface StudentLessonGate {

    /**
     * 어느 소속 반 진도표에도 없으면(다른 권·다른 학원·퇴원·끝난 반) 404 {@code NOT_FOUND}, 진도표에는 있지만 모두 수업일 전·휴강이면 423 {@code LESSON_LOCKED}.
     * 오늘은 학원 타임존 날짜
     */
    void requireOpen(long academyId, long studentId, long bookId, long lessonId);
}
