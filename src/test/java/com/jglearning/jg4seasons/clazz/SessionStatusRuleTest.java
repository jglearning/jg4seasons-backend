package com.jglearning.jg4seasons.clazz;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.clazz.api.SessionStatus;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** 진도표 행의 응답 상태(이슈 #45). 오늘 = 2026-11-10(학원 날짜). */
class SessionStatusRuleTest {

    static final LocalDate TODAY = d(10);

    static LocalDate d(int day) {
        return LocalDate.of(2026, 11, day);
    }

    @Test
    void 어제는_done() {
        assertThat(SessionStatusRule.shown(SessionStatus.SCHEDULED, d(9), TODAY))
                .isEqualTo(SessionStatus.DONE);
    }

    @Test
    void 오늘은_scheduled() {
        assertThat(SessionStatusRule.shown(SessionStatus.SCHEDULED, d(10), TODAY))
                .isEqualTo(SessionStatus.SCHEDULED);
    }

    @Test
    void 미래는_scheduled() {
        assertThat(SessionStatusRule.shown(SessionStatus.SCHEDULED, d(11), TODAY))
                .isEqualTo(SessionStatus.SCHEDULED);
    }

    @Test
    void 휴강은_지났든_아니든_cancelled() {
        assertThat(SessionStatusRule.shown(SessionStatus.CANCELLED, d(9), TODAY))
                .isEqualTo(SessionStatus.CANCELLED);
        assertThat(SessionStatusRule.shown(SessionStatus.CANCELLED, d(11), TODAY))
                .isEqualTo(SessionStatus.CANCELLED);
    }

    @Test
    void 저장된_done도_날짜로_정한다() {
        assertThat(SessionStatusRule.shown(SessionStatus.DONE, d(9), TODAY)).isEqualTo(SessionStatus.DONE);
        assertThat(SessionStatusRule.shown(SessionStatus.DONE, d(11), TODAY)).isEqualTo(SessionStatus.SCHEDULED);
    }
}
