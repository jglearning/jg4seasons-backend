package com.jglearning.jg4seasons.clazz;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.academy.api.LicenseStatus;
import com.jglearning.jg4seasons.clazz.api.LicenseUsage;
import com.jglearning.jg4seasons.clazz.api.LicenseUsages;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 학원의 권별 사용권 + 사용 학생 수(7.3·7.5, ADR 0031 결정 5). 사용 학생 수 = 그 권 active 반에 지금 소속된 서로 다른 학생 — 등록·이동 한도(409)와 같은
 * 숫자. 오늘 = 2026-12-10(학원 날짜).
 */
@IntegrationTest
@Transactional
class LicenseUsagesTest {

    static final LocalDate TODAY = LocalDate.of(2026, 12, 10);

    @Autowired
    LicenseUsages usages;

    @Autowired
    JdbcTemplate jdbc;

    TestData data;
    long academyId;

    @BeforeEach
    void setUp() {
        data = new TestData(jdbc);
        academyId = data.academy("연계 학원");
        data.enforceLicenses(academyId);
    }

    private long activeClass(String bookCode, String name) {
        return data.clazz(academyId, bookCode, name, TestData.uniqueAccessCode(), "active");
    }

    @Test
    void 사용_학생_수는_active_반의_현재_학생을_한_번씩() {
        data.license(academyId, "L2-B1", TODAY.minusDays(10), TODAY.plusDays(20), 30);
        long a = activeClass("L2-B1", "A반");
        long b = activeClass("L2-B1", "B반");
        long finished = data.clazz(academyId, "L2-B1", "끝난 반", TestData.uniqueAccessCode(), "finished");
        long both = data.student(academyId, "두 반");
        long left = data.student(academyId, "퇴원");
        long one = data.student(academyId, "한 반");
        long old = data.student(academyId, "끝난 반 학생");
        data.enroll(a, both);
        data.enroll(b, both);
        data.enroll(a, left);
        data.leave(a, left);
        data.enroll(b, one);
        data.enroll(finished, old);

        List<LicenseUsage> found = usages.of(academyId, TODAY);

        assertThat(found).hasSize(1);
        LicenseUsage usage = found.get(0);
        assertThat(usage.row().bookId()).isEqualTo(data.bookId("L2-B1"));
        assertThat(usage.row().seats()).isEqualTo(30);
        assertThat(usage.usedSeats()).as("두 반 학생 1번, 퇴원·끝난 반 제외").isEqualTo(2);
        assertThat(usage.row().status()).isEqualTo(LicenseStatus.ACTIVE);
        assertThat(usage.row().daysLeft()).isEqualTo(21);
    }

    @Test
    void 사용권_줄이_있는_권만_권_id_순() {
        data.license(academyId, "L2-B2", TODAY.plusDays(5), TODAY.plusDays(60), 10);
        data.license(academyId, "L2-B1", TODAY.minusDays(60), TODAY.minusDays(1), 10);
        activeClass("L2-B3", "사용권 없는 권 반");

        List<LicenseUsage> found = usages.of(academyId, TODAY);

        assertThat(found)
                .extracting(usage -> usage.row().bookId())
                .containsExactly(data.bookId("L2-B1"), data.bookId("L2-B2"));
        assertThat(found)
                .extracting(usage -> usage.row().status())
                .containsExactly(LicenseStatus.EXPIRED, LicenseStatus.BEFORE_START);
        assertThat(found).extracting(usage -> usage.row().daysLeft()).containsOnlyNulls();
    }

    @Test
    void 다른_학원_학생은_세지_않는다() {
        data.license(academyId, "L2-B1", TODAY.minusDays(1), TODAY.plusDays(1), 5);
        long other = data.academy("다른 학원");
        long theirs = data.clazz(other, "L2-B1", "남의 반", TestData.uniqueAccessCode(), "active");
        data.enroll(theirs, data.student(other, "남의 학생"));

        assertThat(usages.of(academyId, TODAY).get(0).usedSeats()).isZero();
    }
}
