package com.jglearning.jg4seasons.academy;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.academy.api.LicenseStatus;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** 사용권 판정(ADR 0031 결정 3·4): 적용 안 함 → ACTIVE, 사용권 없음 → EXPIRED, 기간은 시작일·종료일 둘 다 포함. */
class LicenseRuleTest {

    static final LocalDate START = LocalDate.of(2026, 12, 1);
    static final LocalDate END = LocalDate.of(2026, 12, 31);

    @Test
    void 적용하지_않으면_사용권이_없어도_ACTIVE() {
        assertThat(LicenseRule.status(false, null, null, START)).isEqualTo(LicenseStatus.ACTIVE);
    }

    @Test
    void 적용하지_않으면_기간이_지나도_ACTIVE() {
        assertThat(LicenseRule.status(false, START, END, END.plusDays(1))).isEqualTo(LicenseStatus.ACTIVE);
    }

    @Test
    void 사용권이_없으면_EXPIRED() {
        assertThat(LicenseRule.status(true, null, null, START)).isEqualTo(LicenseStatus.EXPIRED);
    }

    @Test
    void 시작_전날은_BEFORE_START() {
        assertThat(LicenseRule.status(true, START, END, START.minusDays(1))).isEqualTo(LicenseStatus.BEFORE_START);
    }

    @Test
    void 시작일_당일은_ACTIVE() {
        assertThat(LicenseRule.status(true, START, END, START)).isEqualTo(LicenseStatus.ACTIVE);
    }

    @Test
    void 종료일_당일은_ACTIVE() {
        assertThat(LicenseRule.status(true, START, END, END)).isEqualTo(LicenseStatus.ACTIVE);
    }

    @Test
    void 종료_다음날은_EXPIRED() {
        assertThat(LicenseRule.status(true, START, END, END.plusDays(1))).isEqualTo(LicenseStatus.EXPIRED);
    }

    @Test
    void 학생_학습은_ACTIVE만() {
        assertThat(LicenseStatus.ACTIVE.allowsLearning()).isTrue();
        assertThat(LicenseStatus.BEFORE_START.allowsLearning()).isFalse();
        assertThat(LicenseStatus.EXPIRED.allowsLearning()).isFalse();
    }

    @Test
    void 개강_준비는_시작_전과_기간_안() {
        assertThat(LicenseStatus.ACTIVE.allowsPreparation()).isTrue();
        assertThat(LicenseStatus.BEFORE_START.allowsPreparation()).isTrue();
        assertThat(LicenseStatus.EXPIRED.allowsPreparation()).isFalse();
    }

    @Test
    void API_값() {
        assertThat(LicenseStatus.BEFORE_START.value()).isEqualTo("before_start");
        assertThat(LicenseStatus.ACTIVE.value()).isEqualTo("active");
        assertThat(LicenseStatus.EXPIRED.value()).isEqualTo("expired");
    }
}
