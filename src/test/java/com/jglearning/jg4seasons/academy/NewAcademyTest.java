package com.jglearning.jg4seasons.academy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jglearning.jg4seasons.academy.api.NewAcademy;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/** 새 학원 값이 스스로 지키는 규칙(리뷰 #47-8): 이름은 정리 뒤 1~50자, 주소는 AcademyRegistry.SLUG_REGEXP. 사용자 문구는 요청 DTO가 맡는다. */
class NewAcademyTest {

    static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Test
    void 이름을_정리한다() {
        assertThat(new NewAcademy("  연계 학원 ", "partner-a", SEOUL, true).name()).isEqualTo("연계 학원");
    }

    @Test
    void 이름이_비거나_50자를_넘으면_거부() {
        assertThatThrownBy(() -> new NewAcademy("   ", "partner-a", SEOUL, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NewAcademy("가".repeat(51), "partner-a", SEOUL, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 주소_규칙에_맞지_않으면_거부() {
        assertThatThrownBy(() -> new NewAcademy("학원", "My_Academy", SEOUL, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NewAcademy("학원", "a".repeat(31), SEOUL, true))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
