package com.jglearning.jg4seasons.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class UserRoleTest {

    @Test
    void DB_값을_역할로_바꾼다() {
        assertThat(UserRole.fromValue("teacher")).isEqualTo(UserRole.TEACHER);
    }

    @Test
    void 모르는_값이나_대문자는_거부한다() {
        assertThatThrownBy(() -> UserRole.fromValue("owner")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UserRole.fromValue("TEACHER")).isInstanceOf(IllegalArgumentException.class);
    }
}
