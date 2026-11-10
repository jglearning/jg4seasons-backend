package com.jglearning.jg4seasons.clazz;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.clazz.api.SessionStatus;
import com.jglearning.jg4seasons.clazz.persistence.ClassSessionStore.SessionRow;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 회차 열림(STU-03): 수업일 당일부터 열림, 휴강 세션은 열지 않음, 미래 허용은 날짜 조건만 없앰(결정 G). */
class LessonOpeningTest {

    static final LocalDate NOV_5 = LocalDate.of(2026, 11, 5);

    static SessionRow scheduled(LocalDate date) {
        return new SessionRow(1L, 1, 10L, date, SessionStatus.SCHEDULED, null);
    }

    static SessionRow cancelled(LocalDate date) {
        return new SessionRow(1L, 1, 10L, date, SessionStatus.CANCELLED, null);
    }

    @Test
    void 수업일_당일은_열림() {
        assertThat(LessonOpening.isOpen(List.of(scheduled(NOV_5)), NOV_5, false))
                .isTrue();
    }

    @Test
    void 수업일_전날은_잠김() {
        assertThat(LessonOpening.isOpen(List.of(scheduled(NOV_5)), NOV_5.minusDays(1), false))
                .isFalse();
    }

    @Test
    void 지난_수업은_열림() {
        assertThat(LessonOpening.isOpen(List.of(scheduled(NOV_5)), NOV_5.plusDays(30), false))
                .isTrue();
        assertThat(LessonOpening.isOpen(
                        List.of(new SessionRow(1L, 1, 10L, NOV_5, SessionStatus.DONE, null)), NOV_5.plusDays(1), false))
                .as("done")
                .isTrue();
    }

    @Test
    void 휴강만_있으면_잠김() {
        assertThat(LessonOpening.isOpen(List.of(cancelled(NOV_5)), NOV_5.plusDays(1), false))
                .isFalse();
    }

    @Test
    void 휴강_뒤_새_세션이_열리면_열림() {
        List<SessionRow> rows = List.of(cancelled(LocalDate.of(2026, 11, 3)), scheduled(NOV_5));

        assertThat(LessonOpening.isOpen(rows, NOV_5.minusDays(1), false))
                .as("새 수업일 전")
                .isFalse();
        assertThat(LessonOpening.isOpen(rows, NOV_5, false)).as("새 수업일").isTrue();
    }

    @Test
    void 미래_허용이면_날짜와_무관하게_열림() {
        assertThat(LessonOpening.isOpen(List.of(scheduled(NOV_5)), NOV_5.minusDays(30), true))
                .isTrue();
    }

    @Test
    void 미래_허용이어도_휴강만_있으면_잠김() {
        assertThat(LessonOpening.isOpen(List.of(cancelled(NOV_5)), NOV_5.plusDays(1), true))
                .isFalse();
    }

    @Test
    void 세션이_없으면_잠김() {
        assertThat(LessonOpening.isOpen(List.of(), NOV_5, true)).isFalse();
    }
}
