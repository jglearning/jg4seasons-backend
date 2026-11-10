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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * 개강 준비 쓰기의 사용권 판정(ADR 0031 결정 4): 새 반(5.1)·진도표 편집(4.6·4.7)은 시작 전·기간 안만, 그 밖 403 LICENSE_INACTIVE. 기록 보기(4.5)와 반
 * 정보 수정(5.1 PATCH)은 만료 뒤에도 된다. 사용권을 적용하는 학원, 오늘 = 서울 2026-11-10. 상태마다 테스트를 나눈다(한 테스트 = 한 영속성 컨텍스트).
 */
@IntegrationTest
@Transactional
class LicensePreparationTest {

    static final Instant NOON_KST = Instant.parse("2026-11-10T03:00:00Z");
    static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

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
    long teacherId;
    long classId;

    @BeforeEach
    void setUp() {
        clock.set(NOON_KST);
        data = new TestData(jdbc);
        academyId = data.academy("연계 학원");
        data.enforceLicenses(academyId);
        adminId = data.staff(academyId, "관리자", "admin");
        teacherId = data.staff(academyId, "김교사", "teacher");
        classId = data.clazz(academyId, "L2-B1", "담당 반", TestData.uniqueAccessCode(), "active");
        data.assignTeacher(classId, teacherId);
        data.session(classId, 1, "L2-B1-01", TODAY.minusDays(5), "scheduled");
        data.session(classId, 2, "L2-B1-02", TODAY.plusDays(2), "scheduled");
        data.session(classId, 3, "L2-B1-03", TODAY.plusDays(7), "scheduled");
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    private void expired() {
        data.license(academyId, "L2-B1", TODAY.minusDays(60), TODAY.minusDays(1), 30);
    }

    private void beforeStart() {
        data.license(academyId, "L2-B1", TODAY.plusDays(1), TODAY.plusDays(60), 30);
    }

    private MvcTestResult send(HttpMethod method, Cookie access, String uri, String body) {
        return mvc.method(method)
                .uri(uri)
                .cookie(access, AuthCookieSupport.csrf())
                .header(AuthCookieSupport.CSRF_HEADER, AuthCookieSupport.CSRF_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private Cookie admin() {
        return cookies.access(adminId, UserRole.ADMIN, academyId);
    }

    private Cookie teacher() {
        return cookies.access(teacherId, UserRole.TEACHER, academyId);
    }

    private MvcTestResult createClass(String bookCode) {
        return send(
                HttpMethod.POST,
                admin(),
                "/api/v1/admin/classes",
                "{\"name\":\"새 반\",\"bookCode\":\"" + bookCode + "\",\"startDate\":\"2026-11-16\","
                        + "\"weekdays\":[1,3],\"teacherIds\":[" + teacherId + "]}");
    }

    private MvcTestResult cancel(int seq) {
        return send(
                HttpMethod.POST,
                teacher(),
                "/api/v1/teacher/classes/" + classId + "/sessions/" + seq + "/cancel",
                "{\"shiftFollowing\":true}");
    }

    private MvcTestResult update(int seq, String body) {
        return send(HttpMethod.PATCH, teacher(), "/api/v1/teacher/classes/" + classId + "/sessions/" + seq, body);
    }

    private static void assertInactive(MvcTestResult result) {
        assertThat(result).hasStatus(403);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("LICENSE_INACTIVE");
    }

    private long classCount() {
        Long n = jdbc.queryForObject("select count(*) from `class` where academy_id = ?", Long.class, academyId);
        return n == null ? 0 : n;
    }

    private String scheduleSnapshot() {
        return String.join(
                ",",
                jdbc.query(
                        "select concat(seq, ' ', scheduled_date, ' ', status, ' ', coalesce(note, '-'))"
                                + " from class_session where class_id = ? order by seq",
                        (rs, i) -> rs.getString(1),
                        classId));
    }

    @Test
    void 만료된_권으로_새_반은_403이고_아무것도_만들지_않는다() {
        expired();

        assertInactive(createClass("L2-B1"));
        assertThat(classCount()).isEqualTo(1);
    }

    @Test
    void 사용권이_없는_권으로_새_반은_403() {
        assertInactive(createClass("L2-B2"));
        assertThat(classCount()).isEqualTo(1);
    }

    @Test
    void 시작_전이면_새_반을_만든다() {
        beforeStart();

        assertThat(createClass("L2-B1")).hasStatus(201);
        assertThat(classCount()).isEqualTo(2);
    }

    @Test
    void 사용권을_적용하지_않는_학원은_사용권_없이_새_반을_만든다() {
        jdbc.update("update academy set license_enforced = false where id = ?", academyId);

        assertThat(createClass("L2-B2")).hasStatus(201);
    }

    @Test
    void 잘못된_입력은_사용권보다_먼저_400() {
        expired();

        MvcTestResult result = send(
                HttpMethod.POST,
                admin(),
                "/api/v1/admin/classes",
                "{\"name\":\"  \",\"bookCode\":\"L2-B1\",\"startDate\":\"2026-11-16\",\"weekdays\":[1,3],"
                        + "\"teacherIds\":[" + teacherId + "]}");

        assertThat(result).hasStatus(400);
    }

    @Test
    void 만료_뒤_휴강은_403이고_진도표는_그대로() {
        expired();
        String before = scheduleSnapshot();

        assertInactive(cancel(2));
        assertThat(scheduleSnapshot()).isEqualTo(before);
    }

    @Test
    void 만료_뒤_날짜_변경은_403() {
        expired();

        assertInactive(update(2, "{\"scheduledDate\":\"2026-11-13\"}"));
    }

    @Test
    void 만료_뒤_메모만_바꿔도_403() {
        expired();
        String before = scheduleSnapshot();

        assertInactive(update(1, "{\"note\":\"메모\"}"));
        assertThat(scheduleSnapshot()).isEqualTo(before);
    }

    @Test
    void 시작_전이면_휴강할_수_있다() {
        beforeStart();

        assertThat(cancel(2)).hasStatusOk();
    }

    @Test
    void 만료_뒤에도_진도표를_본다() {
        expired();

        assertThat(mvc.get()
                        .uri("/api/v1/teacher/classes/" + classId + "/sessions")
                        .cookie(teacher())
                        .exchange())
                .hasStatusOk();
    }

    @Test
    void 만료_뒤에도_반_정보는_바꾼다() {
        expired();

        assertThat(send(HttpMethod.PATCH, admin(), "/api/v1/admin/classes/" + classId, "{\"name\":\"바뀐 반\"}"))
                .hasStatusOk();
    }

    @Test
    void 다른_학원_반_진도표는_만료여도_404() {
        expired();
        long other = data.academy("다른 학원");
        long otherTeacher = data.staff(other, "다른 교사", "teacher");

        MvcTestResult result = send(
                HttpMethod.POST,
                cookies.access(otherTeacher, UserRole.TEACHER, other),
                "/api/v1/teacher/classes/" + classId + "/sessions/2/cancel",
                "{\"shiftFollowing\":true}");

        assertThat(result).hasStatus(404);
    }
}
