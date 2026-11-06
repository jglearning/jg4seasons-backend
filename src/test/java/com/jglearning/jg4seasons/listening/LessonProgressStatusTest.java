package com.jglearning.jg4seasons.listening;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** 회차 진도 상태(lesson_progress.status): DB·API 값은 소문자, 모르는 값은 데이터 오류. */
class LessonProgressStatusTest {

    @Test
    void 값과_DB_문자열이_서로_바뀐다() {
        assertThat(LessonProgressStatus.NOT_STARTED.value()).isEqualTo("not_started");
        assertThat(LessonProgressStatus.IN_PROGRESS.value()).isEqualTo("in_progress");
        assertThat(LessonProgressStatus.COMPLETED.value()).isEqualTo("completed");
        for (LessonProgressStatus status : LessonProgressStatus.values()) {
            assertThat(LessonProgressStatus.fromValue(status.value())).isEqualTo(status);
        }
    }

    @Test
    void 모르는_값은_IllegalState() {
        assertThatThrownBy(() -> LessonProgressStatus.fromValue("done")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> LessonProgressStatus.fromValue("COMPLETED")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> LessonProgressStatus.fromValue(null)).isInstanceOf(IllegalStateException.class);
    }
}
