package com.jglearning.jg4seasons.admin.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.MutableClock;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.auth.AuthCookieSupport;
import com.jglearning.jg4seasons.auth.UserRole;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

/** 7.5 학원관리자 교재 사용권 보기(A-05, SYS-07). 학원은 access 쿠키에서만 — 자기 학원 사용권만 본다. 오늘 = 서울 2026-12-10. */
@IntegrationTest
@Transactional
class LicenseAdminTest {

    static final String URL = "/api/v1/admin/licenses";
    /** 서울 2026-12-10 12:00 */
    static final Instant NOON = Instant.parse("2026-12-10T03:00:00Z");

    @Autowired
    MockMvcTester mvc;

    @Autowired
    AuthCookieSupport cookies;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MutableClock clock;

    TestData data;
    long academyId;
    long adminId;

    @BeforeEach
    void setUp() {
        clock.set(NOON);
        data = new TestData(jdbc);
        academyId = data.academy("연계 학원");
        data.enforceLicenses(academyId);
        adminId = data.staff(academyId, "관리자", "admin");
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    private MvcTestResult licenses(Cookie access) {
        return mvc.get().uri(URL).cookie(access).exchange();
    }

    private Cookie admin() {
        return cookies.access(adminId, UserRole.ADMIN, academyId);
    }

    @Test
    void 자기_학원_사용권과_사용_학생_수만_본다() {
        data.license(academyId, "L2-B1", LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 12), 20);
        long classId = data.clazz(academyId, "L2-B1", "A반", TestData.uniqueAccessCode(), "active");
        data.enroll(classId, data.student(academyId, "가"));
        long other = data.academy("다른 학원");
        data.enforceLicenses(other);
        data.license(other, "L2-B2", LocalDate.of(2026, 12, 1), LocalDate.of(2027, 2, 28), 50);

        MvcTestResult result = licenses(admin());

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.licenseEnforced").isEqualTo(true);
        assertThat(result).bodyJson().extractingPath("$.today").isEqualTo("2026-12-10");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.items")
                .isEqualTo(List.of(Map.of(
                        "book",
                        Map.of("code", "L2-B1", "label", "Book 1", "season", "겨울"),
                        "startDate",
                        "2026-12-01",
                        "endDate",
                        "2026-12-12",
                        "seats",
                        20,
                        "usedSeats",
                        1,
                        "status",
                        "active",
                        "daysLeft",
                        3)));
    }

    @Test
    void 사용권을_적용하지_않는_학원은_licenseEnforced_false() {
        long direct = data.academy("직영");
        long directAdmin = data.staff(direct, "직영 관리자", "admin");

        MvcTestResult result = licenses(cookies.access(directAdmin, UserRole.ADMIN, direct));

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.licenseEnforced").isEqualTo(false);
        assertThat(result).bodyJson().extractingPath("$.items").isEqualTo(List.of());
    }

    @Test
    void 교사와_운영자는_403() {
        long teacherId = data.staff(academyId, "교사", "teacher");
        long operatorId = data.operator("운영자");

        assertThat(licenses(cookies.access(teacherId, UserRole.TEACHER, academyId)))
                .hasStatus(403);
        assertThat(licenses(cookies.operatorAccess(operatorId))).hasStatus(403);
    }

    @Test
    void 비활성_관리자는_401() {
        jdbc.update("update `user` set status = 'inactive' where id = ?", adminId);

        assertThat(licenses(admin())).hasStatus(401);
    }
}
