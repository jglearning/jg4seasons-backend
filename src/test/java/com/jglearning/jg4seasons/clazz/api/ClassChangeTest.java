package com.jglearning.jg4seasons.clazz.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 반 변경 값: null 칸은 "바꾸지 않음"(빈 Optional)으로 받는다 — StaffChange와 같은 규칙. */
class ClassChangeTest {

    @Test
    void null_칸은_빈_Optional() {
        ClassChange change = new ClassChange(null, Optional.of(ClassStatus.ARCHIVED), null);

        assertThat(change.name()).isEmpty();
        assertThat(change.status()).contains(ClassStatus.ARCHIVED);
        assertThat(change.allowFutureLessons()).isEmpty();
    }

    @Test
    void 한_칸만_바꾸는_팩터리() {
        assertThat(ClassChange.rename("B반").name()).contains("B반");
        assertThat(ClassChange.rename("B반").status()).isEmpty();
        assertThat(ClassChange.status(ClassStatus.FINISHED).status()).contains(ClassStatus.FINISHED);
    }
}
