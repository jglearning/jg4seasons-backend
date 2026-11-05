package com.jglearning.jg4seasons.clazz;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 진도표 수업일: 개강일(포함)부터 수업 요일에 맞는 날짜. 공휴일은 반영하지 않는다. */
class ClassScheduleTest {

    private static final Weekdays MON_WED = Weekdays.fromNumbers(List.of(1, 3));

    @Test
    void 개강일이_수업_요일이면_1회차() {
        // 2026-11-09 월요일
        List<LocalDate> dates = ClassSchedule.dates(LocalDate.of(2026, 11, 9), MON_WED, 4);

        assertThat(dates)
                .containsExactly(
                        LocalDate.of(2026, 11, 9),
                        LocalDate.of(2026, 11, 11),
                        LocalDate.of(2026, 11, 16),
                        LocalDate.of(2026, 11, 18));
    }

    @Test
    void 개강일이_수업_요일이_아니면_다음_수업_요일부터() {
        // 2026-11-10 화요일
        assertThat(ClassSchedule.dates(LocalDate.of(2026, 11, 10), MON_WED, 2))
                .containsExactly(LocalDate.of(2026, 11, 11), LocalDate.of(2026, 11, 16));
    }

    @Test
    void 일요일은_7() {
        // 2026-11-09 월요일 → 첫 일요일 11/15
        assertThat(ClassSchedule.dates(LocalDate.of(2026, 11, 9), Weekdays.fromNumbers(List.of(7)), 2))
                .containsExactly(LocalDate.of(2026, 11, 15), LocalDate.of(2026, 11, 22));
    }

    @Test
    void 기본_24개이고_마지막_날짜() {
        List<LocalDate> dates = ClassSchedule.dates(LocalDate.of(2026, 11, 9), MON_WED, ClassSchedule.SESSION_COUNT);

        assertThat(ClassSchedule.SESSION_COUNT).isEqualTo(24);
        assertThat(dates).hasSize(24).isSorted().doesNotHaveDuplicates();
        assertThat(dates.get(23)).isEqualTo(LocalDate.of(2027, 1, 27));
    }

    @Test
    void 윤년_2월_29일을_건너뛰지_않는다() {
        // 2028-02-28 월요일, 2/29 화요일
        assertThat(ClassSchedule.dates(LocalDate.of(2028, 2, 28), Weekdays.fromNumbers(List.of(1, 2)), 3))
                .containsExactly(LocalDate.of(2028, 2, 28), LocalDate.of(2028, 2, 29), LocalDate.of(2028, 3, 6));
    }
}
