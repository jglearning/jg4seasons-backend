package com.jglearning.jg4seasons.clazz.api;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** 접은 회차의 규칙: reached면 held·open, open이면 held, 수업일 필수. 어기면 만들 수 없다(LessonBoard가 믿고 판정한다). */
class ClassLessonTest {

    static final LocalDate DAY = LocalDate.of(2026, 11, 10);

    @Test
    void 규칙에_맞는_조합은_만든다() {
        assertThatCode(() -> new ClassLesson(1, 1, DAY, true, true, true)).doesNotThrowAnyException();
        assertThatCode(() -> new ClassLesson(1, 1, DAY, false, true, true))
                .as("미래 허용 반의 수업일 전 회차")
                .doesNotThrowAnyException();
        assertThatCode(() -> new ClassLesson(1, 1, DAY, false, false, true)).doesNotThrowAnyException();
        assertThatCode(() -> new ClassLesson(1, 1, DAY, false, false, false))
                .as("휴강만 있는 회차")
                .doesNotThrowAnyException();
    }

    @Test
    void 도달했으면_열려_있고_수업이_있다() {
        assertThatThrownBy(() -> new ClassLesson(1, 1, DAY, true, false, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ClassLesson(1, 1, DAY, true, true, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 열렸으면_수업이_있다() {
        assertThatThrownBy(() -> new ClassLesson(1, 1, DAY, false, true, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 수업일은_필수() {
        assertThatThrownBy(() -> new ClassLesson(1, 1, null, false, false, false))
                .isInstanceOf(NullPointerException.class);
    }
}
