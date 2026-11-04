package com.jglearning.jg4seasons.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** 교사 계정 역할·상태의 문자열 값(소문자). 모르는 값은 기본값으로 바꾸지 않고 거부한다. */
class StaffValuesTest {

    @Test
    void 역할_값과_변환() {
        assertThat(StaffRole.ADMIN.value()).isEqualTo("admin");
        assertThat(StaffRole.fromValue("teacher")).isEqualTo(StaffRole.TEACHER);
        assertThatThrownBy(() -> StaffRole.fromValue("Admin")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StaffRole.fromValue("superadmin")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 상태_값과_변환() {
        assertThat(AccountStatus.INACTIVE.value()).isEqualTo("inactive");
        assertThat(AccountStatus.fromValue("active")).isEqualTo(AccountStatus.ACTIVE);
        assertThatThrownBy(() -> AccountStatus.fromValue("inactve")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AccountStatus.fromValue("suspended")).isInstanceOf(IllegalArgumentException.class);
    }
}
