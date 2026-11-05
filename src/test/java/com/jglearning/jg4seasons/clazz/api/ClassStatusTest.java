package com.jglearning.jg4seasons.clazz.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** 반 상태의 문자열 값(소문자, DB·API 같음). 모르는 값은 기본값으로 바꾸지 않고 거부한다. */
class ClassStatusTest {

    @Test
    void 값과_변환() {
        assertThat(ClassStatus.ACTIVE.value()).isEqualTo("active");
        assertThat(ClassStatus.FINISHED.value()).isEqualTo("finished");
        assertThat(ClassStatus.fromValue("archived")).isEqualTo(ClassStatus.ARCHIVED);
    }

    @Test
    void 모르는_값과_대소문자가_다른_값은_거부() {
        assertThatThrownBy(() -> ClassStatus.fromValue("Active")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ClassStatus.fromValue("closed")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ClassStatus.fromValue(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
