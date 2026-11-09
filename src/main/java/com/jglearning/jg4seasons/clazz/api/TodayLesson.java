package com.jglearning.jg4seasons.clazz.api;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 오늘 회차 규칙(STU-01, ADR-24): 수업일이 지난(reached) 회차 중 (수업일, seq)가 가장 늦은 것. open으로 고르면 미래 회차 허용 반에서 24회차가
 * "오늘"이 된다. 학생 홈(LessonBoard)과 학원 현황이 같이 쓴다.
 */
public final class TodayLesson {

    /** (수업일, seq) 순 */
    public static final Comparator<ClassLesson> BY_DATE_THEN_SEQ =
            Comparator.comparing(ClassLesson::scheduledDate).thenComparingInt(ClassLesson::seq);

    private TodayLesson() {}

    public static Optional<ClassLesson> of(List<ClassLesson> lessons) {
        return lessons.stream().filter(ClassLesson::reached).max(BY_DATE_THEN_SEQ);
    }
}
