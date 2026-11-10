package com.jglearning.jg4seasons.academy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.academy.api.BookLicenses;
import com.jglearning.jg4seasons.academy.api.LicenseStatus;
import java.time.LocalDate;
import java.util.OptionalInt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** 학원·권 사용권 조회(ADR 0031). 판정 규칙 자체는 LicenseRuleTest. */
@IntegrationTest
@Transactional
class BookLicensesTest {

    static final LocalDate START = LocalDate.of(2026, 12, 1);
    static final LocalDate END = LocalDate.of(2026, 12, 31);

    @Autowired
    BookLicenses licenses;

    @Autowired
    JdbcTemplate jdbc;

    TestData data;
    long book;

    @BeforeEach
    void setUp() {
        data = new TestData(jdbc);
        book = data.bookId("L2-B1");
    }

    @Test
    void 직영_학원은_ACTIVE이고_한도가_없다() {
        long academy = data.academy("직영");

        assertThat(licenses.statusOf(academy, book, START)).isEqualTo(LicenseStatus.ACTIVE);
        assertThat(licenses.seatLimit(academy, book)).isEmpty();
    }

    @Test
    void 적용_학원에_사용권이_없으면_EXPIRED이고_한도_0() {
        long academy = data.academy("연계");
        data.enforceLicenses(academy);

        assertThat(licenses.statusOf(academy, book, START)).isEqualTo(LicenseStatus.EXPIRED);
        assertThat(licenses.seatLimit(academy, book)).isEqualTo(OptionalInt.of(0));
    }

    @Test
    void 적용_학원은_사용권_기간으로_판정한다() {
        long academy = data.academy("연계");
        data.enforceLicenses(academy);
        data.license(academy, "L2-B1", START, END, 30);

        assertThat(licenses.statusOf(academy, book, START.minusDays(1))).isEqualTo(LicenseStatus.BEFORE_START);
        assertThat(licenses.statusOf(academy, book, END)).isEqualTo(LicenseStatus.ACTIVE);
        assertThat(licenses.statusOf(academy, book, END.plusDays(1))).isEqualTo(LicenseStatus.EXPIRED);
        assertThat(licenses.seatLimit(academy, book)).isEqualTo(OptionalInt.of(30));
    }

    @Test
    void 사용권은_권마다_따로다() {
        long academy = data.academy("연계");
        data.enforceLicenses(academy);
        data.license(academy, "L2-B1", START, END, 30);

        assertThat(licenses.statusOf(academy, data.bookId("L2-B2"), START)).isEqualTo(LicenseStatus.EXPIRED);
    }

    @Test
    void 다른_학원의_사용권은_보지_않는다() {
        long owner = data.academy("사용권 있는 학원");
        data.license(owner, "L2-B1", START, END, 30);
        long other = data.academy("다른 연계 학원");
        data.enforceLicenses(other);

        assertThat(licenses.statusOf(other, book, START)).isEqualTo(LicenseStatus.EXPIRED);
        assertThat(licenses.seatLimit(other, book)).isEqualTo(OptionalInt.of(0));
    }

    @Test
    void 학원_권마다_한_줄이다() {
        long academy = data.academy("연계");
        data.license(academy, "L2-B1", START, END, 30);

        assertThatThrownBy(() -> data.license(academy, "L2-B1", START, END, 10))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 종료일이_시작일보다_앞서면_거부된다() {
        long academy = data.academy("연계");

        // MySQL CHECK 위반(3819)은 Spring이 UncategorizedSQLException으로 넘긴다 — 제약 이름으로 확인
        assertThatThrownBy(() -> data.license(academy, "L2-B1", END, START, 30))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_book_license_period");
    }

    @Test
    void 학생_수는_음수일_수_없다() {
        long academy = data.academy("연계");

        assertThatThrownBy(() -> data.license(academy, "L2-B1", START, END, -1))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_book_license_seats");
    }
}
