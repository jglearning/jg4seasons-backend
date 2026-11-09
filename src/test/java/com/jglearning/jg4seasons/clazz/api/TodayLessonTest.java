package com.jglearning.jg4seasons.clazz.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 오늘 회차 = 수업일이 지난(reached) 회차 중 (수업일, seq)가 가장 늦은 것. 학생 홈과 학원 현황이 같은 규칙을 쓴다. */
class TodayLessonTest {

    static final LocalDate D1 = LocalDate.of(2026, 11, 2);
    static final LocalDate D2 = LocalDate.of(2026, 11, 4);

    static ClassLesson reached(long lessonId, int seq, LocalDate date) {
        return new ClassLesson(lessonId, seq, date, true, true, true);
    }

    @Test
    void 도달한_회차_중_가장_늦은_날() {
        assertThat(TodayLesson.of(List.of(reached(2, 2, D2), reached(1, 1, D1))))
                .map(ClassLesson::lessonId)
                .contains(2L);
    }

    @Test
    void 같은_날이면_seq가_큰_것() {
        assertThat(TodayLesson.of(List.of(reached(3, 3, D2), reached(4, 4, D2), reached(1, 1, D1))))
                .map(ClassLesson::lessonId)
                .contains(4L);
    }

    @Test
    void 도달한_회차가_없으면_비어_있다() {
        ClassLesson future = new ClassLesson(1, 1, D2, false, false, true);

        assertThat(TodayLesson.of(List.of(future))).isEmpty();
        assertThat(TodayLesson.of(List.of())).isEmpty();
    }

    @Test
    void 미래_허용_반에서도_도달_기준() {
        // 미래 회차 허용 반: 24회차도 open이지만 오늘 회차가 되면 안 된다
        ClassLesson openFuture = new ClassLesson(24, 24, D2.plusDays(60), false, true, true);

        assertThat(TodayLesson.of(List.of(reached(1, 1, D1), openFuture)))
                .map(ClassLesson::lessonId)
                .contains(1L);
    }
}
