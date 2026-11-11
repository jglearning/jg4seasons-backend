package com.jglearning.jg4seasons.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** 운영자 계정 설정값(jg4seasons.initial-operator.*, ADR 0032) 검사. 값이 없으면 만들지 않고, 반쪽·틀린 값이면 키 이름만 싣고 멈춘다. */
class InitialOperatorPropertiesTest {

    static final String PASSWORD = "operator-pass-1";

    @Test
    void 값이_하나도_없으면_만들지_않는다() {
        assertThat(new InitialOperatorProperties(null, null, null).resolve()).isEmpty();
        assertThat(new InitialOperatorProperties("", " ", "").resolve())
                .as("빠진 환경 변수는 빈 문자열")
                .isEmpty();
    }

    @Test
    void 아이디와_비밀번호가_있으면_정리해서_돌려준다() {
        InitialOperator operator = new InitialOperatorProperties("  Ops.Admin ", PASSWORD, null)
                .resolve()
                .orElseThrow();

        assertThat(operator.loginId()).isEqualTo("ops.admin");
        assertThat(operator.password()).isEqualTo(PASSWORD);
        assertThat(operator.name()).as("이름이 없으면 기본값").isEqualTo("운영자");
    }

    @Test
    void 반쪽이면_빠진_키만_싣고_멈춘다() {
        assertThatThrownBy(() -> new InitialOperatorProperties("ops", null, null).resolve())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("password")
                .hasMessageNotContaining("ops");
    }

    @Test
    void 비밀번호가_10자보다_짧으면_멈추고_값은_싣지_않는다() {
        assertThatThrownBy(() -> new InitialOperatorProperties("ops", "short-pw", null).resolve())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("password")
                .hasMessageNotContaining("short-pw");
    }

    @Test
    void 비밀번호가_아이디와_같으면_멈춘다() {
        assertThatThrownBy(() -> new InitialOperatorProperties("operator-01", "OPERATOR-01", null).resolve())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 아이디_형식이_틀리면_멈춘다() {
        assertThatThrownBy(() -> new InitialOperatorProperties("운영자", PASSWORD, null).resolve())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("login-id");
    }

    @Test
    void 이름이_30자를_넘으면_멈춘다() {
        assertThatThrownBy(() -> new InitialOperatorProperties("ops", PASSWORD, "가".repeat(31)).resolve())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("name");
    }

    @Test
    void toString에_비밀번호가_없다() {
        assertThat(new InitialOperatorProperties("ops", PASSWORD, null).toString())
                .doesNotContain(PASSWORD);
    }
}
