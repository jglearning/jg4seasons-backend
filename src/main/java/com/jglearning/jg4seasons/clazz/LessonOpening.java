package com.jglearning.jg4seasons.clazz;

import com.jglearning.jg4seasons.clazz.persistence.ClassSessionStore.SessionRow;
import java.time.LocalDate;
import java.util.List;

/**
 * 회차 열림 규칙(STU-03, ADR-09). 한 회차의 진도표 행들 중 휴강(cancelled)이 아닌 것이 하나라도 열렸으면 열림. 열림 = 수업일이 오늘(학원 타임존)
 * 이하, 또는 반이 미래 회차를 허용. 휴강 시프트 뒤 같은 회차가 cancelled 행과 새 행으로 둘일 수 있다.
 */
final class LessonOpening {

    static final String CANCELLED = "cancelled";

    private LessonOpening() {}

    static boolean isOpen(List<SessionRow> sessionsOfLesson, LocalDate today, boolean allowFuture) {
        return sessionsOfLesson.stream()
                .filter(session -> !CANCELLED.equals(session.status()))
                .anyMatch(session -> allowFuture || !session.scheduledDate().isAfter(today));
    }
}
