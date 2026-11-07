package com.jglearning.jg4seasons.infra.persistence.devseed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import org.junit.jupiter.api.Test;

/** 시드 반의 가입 코드. local은 공개 값(G7KM2XP4)을 쓰고, 운영 시험(trial)은 Parameter Store 값만 — 공개 값·빈 값·틀린 형식이면 서버가 뜨지 않는다. */
class SeedAccessCodeTest {

    @Test
    void local은_값이_없으면_공개_코드를_쓴다() {
        assertThat(SeedAccessCode.resolve(null, false)).isEqualTo("G7KM2XP4");
        assertThat(SeedAccessCode.resolve(" ", false)).isEqualTo("G7KM2XP4");
    }

    @Test
    void 값이_있으면_공백을_떼고_대문자로() {
        assertThat(SeedAccessCode.resolve(" hq7w2xm9 ", true)).isEqualTo("HQ7W2XM9");
        assertThat(SeedAccessCode.resolve("HQ7W2XM9", false)).isEqualTo("HQ7W2XM9");
    }

    @Test
    void trial에서_값이_없으면_서버_시작을_멈춘다() {
        assertThatIllegalStateException()
                .isThrownBy(() -> SeedAccessCode.resolve("", true))
                .withMessageContaining("jg4seasons.seed.access-code");
        assertThatIllegalStateException().isThrownBy(() -> SeedAccessCode.resolve(null, true));
    }

    // 공개 저장소에 있는 코드로 운영 반이 생기면 누구나 들어온다
    @Test
    void trial에서_공개_코드면_서버_시작을_멈춘다() {
        assertThatIllegalStateException()
                .isThrownBy(() -> SeedAccessCode.resolve("g7km2xp4", true))
                .withMessageContaining("공개");
    }

    // 가입 코드 생성 규칙(AccessCodeGenerator)과 같게: 8자, 헷갈리는 글자(I·L·O·0·1) 없음. 메시지에 값은 싣지 않는다
    @Test
    void 형식이_틀리면_서버_시작을_멈추고_값은_싣지_않는다() {
        assertThatIllegalStateException()
                .isThrownBy(() -> SeedAccessCode.resolve("HQ7W2XM", true))
                .withMessageContaining("8자")
                .withMessageNotContaining("HQ7W2XM");
        assertThatIllegalStateException()
                .isThrownBy(() -> SeedAccessCode.resolve("HQ7W2XO1", true))
                .withMessageNotContaining("HQ7W2XO1");
        assertThatIllegalStateException().isThrownBy(() -> SeedAccessCode.resolve("HQ7W-XM9", false));
    }
}
