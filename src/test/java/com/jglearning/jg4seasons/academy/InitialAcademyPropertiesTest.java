package com.jglearning.jg4seasons.academy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import org.junit.jupiter.api.Test;

/** 첫 학원·관리자 설정(jg4seasons.initial) 검사. 반쪽·잘못된 설정이면 서버가 뜨지 않게 예외, 메시지에 비밀번호 값은 없다. */
class InitialAcademyPropertiesTest {

    static final String PASSWORD = "first-admin-password";

    static InitialAcademyProperties props(
            String name, String slug, String loginId, String password, String adminName, boolean required) {
        return new InitialAcademyProperties(name, slug, loginId, password, adminName, required);
    }

    @Test
    void 값이_모두_있으면_정리한_값을_돌려준다() {
        InitialAcademy initial = props(" 첫 학원 ", " main ", " Owner ", PASSWORD, " 원장 ", true)
                .resolve()
                .orElseThrow();

        assertThat(initial.academyName()).isEqualTo("첫 학원");
        assertThat(initial.academySlug()).isEqualTo("main");
        assertThat(initial.adminLoginId()).isEqualTo("owner");
        assertThat(initial.adminPassword()).isEqualTo(PASSWORD);
        assertThat(initial.adminName()).isEqualTo("원장");
        assertThat(initial.toString()).doesNotContain(PASSWORD);
    }

    @Test
    void 관리자_이름이_없으면_기본_이름() {
        InitialAcademy initial =
                props("첫 학원", "main", "owner", PASSWORD, " ", false).resolve().orElseThrow();

        assertThat(initial.adminName()).isEqualTo("관리자");
    }

    @Test
    void 값이_하나도_없고_필수가_아니면_만들지_않는다() {
        assertThat(props(null, "", " ", null, null, false).resolve()).isEmpty();
    }

    // 운영에서 아무도 로그인할 수 없는 서버가 정상인 것처럼 뜨면 안 된다
    @Test
    void 값이_하나도_없는데_필수면_서버_시작을_멈춘다() {
        assertThatIllegalStateException()
                .isThrownBy(() -> props(null, null, null, null, null, true).resolve())
                .withMessageContaining("jg4seasons.initial");
    }

    // 환경 변수 하나를 빠뜨린 경우. "설정이 없다"와 구분하고, 빠진 키 이름을 알리되 값은 싣지 않는다
    @Test
    void 반쪽_설정이면_빠진_키를_알리고_서버_시작을_멈춘다() {
        assertThatIllegalStateException()
                .isThrownBy(() ->
                        props(null, "main", "owner", PASSWORD, null, false).resolve())
                .withMessageContaining("academy-name")
                .withMessageNotContaining(PASSWORD);
        assertThatIllegalStateException()
                .isThrownBy(
                        () -> props("첫 학원", "main", "owner", " ", null, false).resolve())
                .withMessageContaining("admin-password");
        assertThatIllegalStateException()
                .isThrownBy(
                        () -> props("첫 학원", null, null, PASSWORD, null, false).resolve())
                .withMessageContaining("academy-slug")
                .withMessageContaining("admin-login-id")
                .withMessageNotContaining(PASSWORD);
    }

    @Test
    void 비밀번호가_짧거나_72바이트를_넘으면_서버_시작을_멈춘다() {
        assertThatIllegalStateException()
                .isThrownBy(() ->
                        props("첫 학원", "main", "owner", "short-pw1", null, false).resolve())
                .withMessageContaining("10자 이상")
                .withMessageNotContaining("short-pw1");
        // 한글 25자 = 75바이트. BCrypt는 72바이트 뒤를 버린다
        assertThatIllegalStateException()
                .isThrownBy(() -> props("첫 학원", "main", "owner", "가".repeat(25), null, false)
                        .resolve())
                .withMessageContaining("72바이트");
    }

    @Test
    void 비밀번호가_아이디와_같거나_제어_문자가_있으면_서버_시작을_멈춘다() {
        assertThatIllegalStateException()
                .isThrownBy(() -> props("첫 학원", "main", "owner-admin", "Owner-Admin", null, false)
                        .resolve())
                .withMessageContaining("아이디와 달라야")
                .withMessageNotContaining("Owner-Admin");
        assertThatIllegalStateException()
                .isThrownBy(() -> props("첫 학원", "main", "owner", PASSWORD + "\n", null, false)
                        .resolve())
                .withMessageContaining("제어 문자");
    }

    @Test
    void 아이디_slug_이름_형식이_틀리면_서버_시작을_멈춘다() {
        assertThatIllegalStateException()
                .isThrownBy(() ->
                        props("첫 학원", "main", "원장님", PASSWORD, null, false).resolve())
                .withMessageContaining("admin-login-id");
        assertThatIllegalStateException()
                .isThrownBy(() -> props("첫 학원", "Main Campus", "owner", PASSWORD, null, false)
                        .resolve())
                .withMessageContaining("academy-slug");
        assertThatIllegalStateException()
                .isThrownBy(() -> props("가".repeat(51), "main", "owner", PASSWORD, null, false)
                        .resolve())
                .withMessageContaining("academy-name");
        assertThatIllegalStateException()
                .isThrownBy(() -> props("첫 학원", "main", "owner", PASSWORD, "가".repeat(31), false)
                        .resolve())
                .withMessageContaining("admin-name");
    }

    @Test
    void toString에_비밀번호가_없다() {
        assertThat(props("첫 학원", "main", "owner", PASSWORD, null, true).toString())
                .doesNotContain(PASSWORD);
    }
}
