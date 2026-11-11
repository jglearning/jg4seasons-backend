package com.jglearning.jg4seasons.system.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.MutableClock;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.auth.AuthCookieSupport;
import com.jglearning.jg4seasons.auth.UserRole;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * 7.3·7.4 운영자 학원 상세·사용권 지정(SYS-07, ADR 0031). 학원·권마다 한 줄 — 처음 PUT은 만들고 다시 PUT은 고친다. 바꾼 값은 다음 요청부터 SYS-06 판정에
 * 쓰인다. 오늘 = 서울 2026-12-10.
 */
@IntegrationTest
@Transactional
class SystemLicenseTest {

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
    long operatorId;
    long academyId;

    @BeforeEach
    void setUp() {
        clock.set(NOON);
        data = new TestData(jdbc);
        operatorId = data.operator("운영자");
        academyId = data.academy("연계 학원");
        data.enforceLicenses(academyId);
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    private MvcTestResult put(Cookie access, long academy, String bookCode, String body) {
        return mvc.put()
                .uri("/api/v1/system/academies/" + academy + "/licenses/" + bookCode)
                .cookie(access, AuthCookieSupport.csrf())
                .header(AuthCookieSupport.CSRF_HEADER, AuthCookieSupport.CSRF_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private MvcTestResult put(String bookCode, String start, String end, int seats) {
        return put(
                cookies.operatorAccess(operatorId),
                academyId,
                bookCode,
                "{\"startDate\":\"" + start + "\",\"endDate\":\"" + end + "\",\"seats\":" + seats + "}");
    }

    private MvcTestResult detail(long academy) {
        return mvc.get()
                .uri("/api/v1/system/academies/" + academy)
                .cookie(cookies.operatorAccess(operatorId))
                .exchange();
    }

    private Integer count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private static void assertValidation(MvcTestResult result, String field) {
        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION");
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo(field);
    }

    @Test
    void 처음_PUT은_만들고_다시_PUT은_고치며_감사_기록을_남긴다() {
        MvcTestResult first = put("L2-B1", "2026-12-01", "2027-02-28", 30);

        assertThat(first).hasStatusOk();
        assertThat(first).bodyJson().extractingPath("$.book.code").isEqualTo("L2-B1");
        assertThat(first).bodyJson().extractingPath("$.seats").asNumber().isEqualTo(30);
        assertThat(first).bodyJson().extractingPath("$.usedSeats").asNumber().isEqualTo(0);
        assertThat(first).bodyJson().extractingPath("$.status").isEqualTo("active");
        assertThat(first).bodyJson().extractingPath("$.daysLeft").asNumber().isEqualTo(81);

        assertThat(put("L2-B1", "2026-12-01", "2027-05-31", 40)).hasStatusOk();

        assertThat(count("select count(*) from book_license where academy_id = ?", academyId))
                .isEqualTo(1);
        assertThat(jdbc.queryForMap("select end_date, seats from book_license where academy_id = ?", academyId))
                .containsEntry("seats", 40);
        List<String> details = jdbc.queryForList(
                "select detail from audit_log where academy_id = ? and action = 'LICENSE_SET' order by id",
                String.class,
                academyId);
        assertThat(details).hasSize(2);
        assertThat((Object) JsonPath.read(details.get(0), "$.before"))
                .as("처음은 전 값 없음")
                .isNull();
        assertThat((String) JsonPath.read(details.get(0), "$.bookCode")).isEqualTo("L2-B1");
        assertThat((String) JsonPath.read(details.get(1), "$.before.endDate")).isEqualTo("2027-02-28");
        assertThat((String) JsonPath.read(details.get(1), "$.after.endDate")).isEqualTo("2027-05-31");
        assertThat((Integer) JsonPath.read(details.get(1), "$.after.seats")).isEqualTo(40);
    }

    @Test
    void 상세는_학원과_권별_현황() {
        put("L2-B2", "2026-12-20", "2027-03-31", 10);
        put("L2-B1", "2026-12-01", "2026-12-10", 2);
        long classId = data.clazz(academyId, "L2-B1", "A반", TestData.uniqueAccessCode(), "active");
        data.enroll(classId, data.student(academyId, "가"));
        data.enroll(classId, data.student(academyId, "나"));
        data.enroll(classId, data.student(academyId, "다"));

        MvcTestResult result = detail(academyId);

        assertThat(result).hasStatusOk();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.academy.licenseEnforced")
                .isEqualTo(true);
        assertThat(result).bodyJson().extractingPath("$.today").isEqualTo("2026-12-10");
        assertThat(result).bodyJson().extractingPath("$.licenses[*].book.code").isEqualTo(List.of("L2-B1", "L2-B2"));
        assertThat(result)
                .bodyJson()
                .extractingPath("$.licenses[0]")
                .isEqualTo(Map.of(
                        "book",
                        Map.of("code", "L2-B1", "label", "Book 1", "season", "겨울"),
                        "startDate",
                        "2026-12-01",
                        "endDate",
                        "2026-12-10",
                        "seats",
                        2,
                        "usedSeats",
                        3,
                        "status",
                        "active",
                        "daysLeft",
                        1));
        assertThat(result).bodyJson().extractingPath("$.licenses[1].status").isEqualTo("before_start");
        assertThat(result).bodyJson().extractingPath("$.licenses[1].daysLeft").isNull();
    }

    @Test
    void 사용권을_넣자마자_학생이_들을_수_있고_종료일을_앞당기면_바로_막힌다() {
        long classId = data.clazz(academyId, "L2-B1", "A반", TestData.uniqueAccessCode(), "active");
        long studentId = data.student(academyId, "김학생");
        data.enroll(classId, studentId);
        data.session(classId, 1, "L2-B1-01", java.time.LocalDate.of(2026, 12, 10), "scheduled");
        data.lessonContent("L2-B1-01", 1, 1, List.of("us_w"));
        Cookie student = cookies.studentAccess(studentId, academyId);
        String lesson = "/api/v1/student/lessons/L2-B1-01";
        assertThat(mvc.get().uri(lesson).cookie(student)).as("사용권 없음").hasStatus(403);

        put("L2-B1", "2026-12-01", "2027-02-28", 30);
        assertThat(mvc.get().uri(lesson).cookie(student)).as("넣은 뒤").hasStatusOk();

        put("L2-B1", "2026-12-01", "2026-12-09", 30);
        assertThat(mvc.get().uri(lesson).cookie(student)).as("어제로 끝냄").hasStatus(403);
    }

    @Test
    void 한도를_사용_학생보다_낮춰도_되고_그_뒤_새_등록만_409() {
        long classId = data.clazz(academyId, "L2-B1", "A반", TestData.uniqueAccessCode(), "active");
        data.enroll(classId, data.student(academyId, "가"));
        data.enroll(classId, data.student(academyId, "나"));
        long adminId = data.staff(academyId, "관리자", "admin");

        MvcTestResult lowered = put("L2-B1", "2026-12-01", "2027-02-28", 1);

        assertThat(lowered).hasStatusOk();
        assertThat(lowered).bodyJson().extractingPath("$.usedSeats").asNumber().isEqualTo(2);
        MvcTestResult join = mvc.post()
                .uri("/api/v1/admin/classes/" + classId + "/students")
                .cookie(cookies.access(adminId, UserRole.ADMIN, academyId), AuthCookieSupport.csrf())
                .header(AuthCookieSupport.CSRF_HEADER, AuthCookieSupport.CSRF_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"students\":[{\"name\":\"다\",\"school\":null,\"grade\":null}]}")
                .exchange();
        assertThat(join).hasStatus(409);
        assertThat(join).bodyJson().extractingPath("$.code").isEqualTo("LICENSE_SEATS_FULL");
    }

    @Test
    void 오늘은_학원_타임존_날짜로_센다() {
        // UTC 12-09 16:00 = 서울 12-10 01:00 — UTC 날짜로 세면 오늘이 12-09, 마지막 날 남은 일수가 2(리뷰 #47-2)
        clock.set(Instant.parse("2026-12-09T16:00:00Z"));
        put("L2-B1", "2026-12-01", "2026-12-10", 30);

        MvcTestResult result = detail(academyId);

        assertThat(result).bodyJson().extractingPath("$.today").isEqualTo("2026-12-10");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.licenses[0].daysLeft")
                .asNumber()
                .isEqualTo(1);
        assertThat(result).bodyJson().extractingPath("$.licenses[0].status").isEqualTo("active");
    }

    @Test
    void 사용권을_적용하지_않는_학원도_지정되고_늘_active() {
        long direct = data.academy("직영");

        MvcTestResult result = put(
                cookies.operatorAccess(operatorId),
                direct,
                "L2-B1",
                "{\"startDate\":\"2026-11-01\",\"endDate\":\"2026-11-30\",\"seats\":5}");

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.status").as("지난 기간이어도").isEqualTo("active");
        assertThat(result).bodyJson().extractingPath("$.daysLeft").isNull();
        MvcTestResult shown = detail(direct);
        assertThat(shown).bodyJson().extractingPath("$.academy.licenseEnforced").isEqualTo(false);
        assertThat(shown)
                .bodyJson()
                .extractingPath("$.licenses.length()")
                .asNumber()
                .isEqualTo(1);
    }

    @Test
    void 정지된_학원도_상세와_사용권_지정이_된다() {
        jdbc.update("update academy set status = 'suspended' where id = ?", academyId);

        assertThat(put("L2-B1", "2026-12-01", "2026-12-31", 30)).hasStatusOk();
        assertThat(detail(academyId))
                .bodyJson()
                .extractingPath("$.academy.status")
                .isEqualTo("suspended");
    }

    @Test
    void 종료일이_시작일보다_앞이면_400() {
        assertValidation(put("L2-B1", "2026-12-10", "2026-12-09", 30), "endDate");
    }

    @Test
    void 학생_수가_범위_밖이면_400() {
        assertValidation(put("L2-B1", "2026-12-01", "2026-12-31", -1), "seats");
        assertValidation(put("L2-B1", "2026-12-01", "2026-12-31", 10001), "seats");
    }

    @Test
    void 모르는_권이나_없는_학원은_404() {
        assertThat(put("L9-B9", "2026-12-01", "2026-12-31", 30)).hasStatus(404);
        assertThat(put(
                        cookies.operatorAccess(operatorId),
                        Long.MAX_VALUE,
                        "L2-B1",
                        "{\"startDate\":\"2026-12-01\",\"endDate\":\"2026-12-31\",\"seats\":1}"))
                .hasStatus(404);
        assertThat(detail(Long.MAX_VALUE)).hasStatus(404);
    }

    @Test
    void 운영자가_아니면_403() {
        long adminId = data.staff(academyId, "관리자", "admin");
        Cookie admin = cookies.access(adminId, UserRole.ADMIN, academyId);

        assertThat(put(
                        admin,
                        academyId,
                        "L2-B1",
                        "{\"startDate\":\"2026-12-01\",\"endDate\":\"2026-12-31\",\"seats\":1}"))
                .hasStatus(403);
        assertThat(mvc.get().uri("/api/v1/system/academies/" + academyId).cookie(admin))
                .hasStatus(403);
    }

    @Test
    void 비활성_운영자는_401() {
        jdbc.update("update `user` set status = 'inactive' where id = ?", operatorId);

        assertThat(put("L2-B1", "2026-12-01", "2026-12-31", 30)).hasStatus(401);
        assertThat(detail(academyId)).hasStatus(401);
    }
}
