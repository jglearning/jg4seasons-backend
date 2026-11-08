package com.jglearning.jg4seasons.admin.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.auth.AuthCookieSupport;
import com.jglearning.jg4seasons.auth.UserRole;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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
 * 5.2 학생 관리·5.4 현황 계약(구현 전 501). 검증(400)과 접근 규칙(401·403)은 지금도 진짜로 동작하고, 통과한 요청만 501을 받는다. 구현 이슈에서 501 단언을 실제
 * 동작 단언으로 바꾼다.
 */
@IntegrationTest
@Transactional
class StudentAdminContractTest {

    /** 구현 전 경로: 메서드 · 경로 · 유효한 본문(null = 본문 없음) */
    record Call(HttpMethod method, String path, String body) {}

    static final List<Call> CALLS = List.of(
            new Call(HttpMethod.GET, "/api/v1/admin/classes/1/students", null),
            new Call(
                    HttpMethod.POST,
                    "/api/v1/admin/classes/1/students",
                    "{\"students\":[{\"name\":\"김민준\",\"school\":\"A중\",\"grade\":\"중1\"},{\"name\":\"이서연\"}]}"),
            new Call(
                    HttpMethod.POST,
                    "/api/v1/admin/classes/1/students/personal-codes/regenerate",
                    "{\"studentIds\":[1,2]}"),
            new Call(HttpMethod.PATCH, "/api/v1/admin/students/1", "{\"name\":\"김민준\",\"status\":\"inactive\"}"),
            new Call(HttpMethod.POST, "/api/v1/admin/students/1/move", "{\"toClassId\":2}"),
            new Call(HttpMethod.POST, "/api/v1/admin/students/search", "{\"name\":\"김\"}"),
            new Call(HttpMethod.GET, "/api/v1/admin/dashboard", null));

    @Autowired
    MockMvcTester mvc;

    @Autowired
    AuthCookieSupport cookies;

    @Autowired
    JdbcTemplate jdbc;

    long academyId;
    long adminId;
    long teacherId;

    @BeforeEach
    void setUp() {
        TestData data = new TestData(jdbc);
        academyId = data.academy("학생 계약 학원");
        adminId = data.staff(academyId, "관리자", "admin");
        teacherId = data.staff(academyId, "김교사", "teacher");
    }

    private Cookie[] as(long userId, UserRole role) {
        return new Cookie[] {cookies.access(userId, role, academyId), AuthCookieSupport.csrf()};
    }

    private MvcTestResult call(Call c, Cookie[] who, boolean withCsrf) {
        var request = mvc.method(c.method()).uri(c.path());
        if (who != null) {
            request = request.cookie(who);
        }
        if (withCsrf) {
            request = request.header(AuthCookieSupport.CSRF_HEADER, AuthCookieSupport.CSRF_VALUE);
        }
        if (c.body() != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(c.body());
        }
        return request.exchange();
    }

    private MvcTestResult asAdmin(HttpMethod method, String path, String body) {
        return call(new Call(method, path, body), as(adminId, UserRole.ADMIN), true);
    }

    private static void assertValidation(MvcTestResult result, String field) {
        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION");
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo(field);
    }

    @Test
    void 관리자의_올바른_요청은_501_NOT_IMPLEMENTED() {
        for (Call c : CALLS) {
            MvcTestResult result = call(c, as(adminId, UserRole.ADMIN), true);

            assertThat(result).as(c.method() + " " + c.path()).hasStatus(501);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("NOT_IMPLEMENTED");
        }
    }

    @Test
    void 교사는_403() {
        for (Call c : CALLS) {
            assertThat(call(c, as(teacherId, UserRole.TEACHER), true))
                    .as(c.method() + " " + c.path())
                    .hasStatus(403);
        }
    }

    @Test
    void 쿠키가_없으면_401() {
        assertThat(call(CALLS.get(0), null, false)).hasStatus(401);
        assertThat(call(CALLS.get(6), null, false)).hasStatus(401);
    }

    @Test
    void 상태를_바꾸는_요청에_X_CSRF가_없으면_403() {
        for (Call c : CALLS) {
            if (c.method() != HttpMethod.GET) {
                assertThat(call(c, as(adminId, UserRole.ADMIN), false))
                        .as(c.method() + " " + c.path())
                        .hasStatus(403);
            }
        }
    }

    @Test
    void 학생_등록_요청이_틀리면_400() {
        String url = "/api/v1/admin/classes/1/students";
        String fiftyOne = IntStream.rangeClosed(1, 51)
                .mapToObj(i -> "{\"name\":\"학생" + i + "\"}")
                .collect(Collectors.joining(",", "{\"students\":[", "]}"));

        assertValidation(asAdmin(HttpMethod.POST, url, "{\"students\":[]}"), "students");
        assertValidation(asAdmin(HttpMethod.POST, url, fiftyOne), "students");
        assertValidation(asAdmin(HttpMethod.POST, url, "{\"students\":[{\"name\":\"  \"}]}"), "students[0].name");
        assertValidation(
                asAdmin(HttpMethod.POST, url, "{\"students\":[{\"name\":\"" + "가".repeat(31) + "\"}]}"),
                "students[0].name");
        assertValidation(
                asAdmin(
                        HttpMethod.POST,
                        url,
                        "{\"students\":[{\"name\":\"a\",\"school\":\"" + "가".repeat(51) + "\"}]}"),
                "students[0].school");
        assertValidation(
                asAdmin(HttpMethod.POST, url, "{\"students\":[{\"name\":\"a\",\"grade\":\"" + "가".repeat(11) + "\"}]}"),
                "students[0].grade");
    }

    @Test
    void 명세에_없는_칸은_400() {
        // 학생 개인정보는 이름·학교·학년만 — 전화 같은 칸은 받지 않는다
        MvcTestResult result = asAdmin(
                HttpMethod.POST,
                "/api/v1/admin/classes/1/students",
                "{\"students\":[{\"name\":\"a\",\"phone\":\"010\"}]}");

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION");
    }

    @Test
    void 재발급_이동_변경_검색_요청이_틀리면_400() {
        String regenerate = "/api/v1/admin/classes/1/students/personal-codes/regenerate";
        assertValidation(asAdmin(HttpMethod.POST, regenerate, "{\"studentIds\":[]}"), "studentIds");
        assertValidation(asAdmin(HttpMethod.POST, regenerate, "{\"studentIds\":[null]}"), "studentIds[0]");

        assertValidation(asAdmin(HttpMethod.PATCH, "/api/v1/admin/students/1", "{\"status\":\"left\"}"), "status");
        assertValidation(
                asAdmin(HttpMethod.PATCH, "/api/v1/admin/students/1", "{\"name\":\"" + "가".repeat(31) + "\"}"), "name");

        assertValidation(asAdmin(HttpMethod.POST, "/api/v1/admin/students/1/move", "{}"), "toClassId");

        assertValidation(asAdmin(HttpMethod.POST, "/api/v1/admin/students/search", "{\"name\":\" \"}"), "name");
    }

    @Test
    void 학생_변경의_칸_검사() {
        String url = "/api/v1/admin/students/1";
        assertValidation(asAdmin(HttpMethod.PATCH, url, "{\"name\":\"  \"}"), "name");
        assertValidation(asAdmin(HttpMethod.PATCH, url, "{\"name\":\" \\n \"}"), "name");
        assertValidation(asAdmin(HttpMethod.PATCH, url, "{\"name\":\"\"}"), "name");
        assertValidation(asAdmin(HttpMethod.PATCH, url, "{\"school\":\"" + "가".repeat(51) + "\"}"), "school");
        assertValidation(asAdmin(HttpMethod.PATCH, url, "{\"grade\":\"" + "가".repeat(11) + "\"}"), "grade");
        // 앞뒤·사이 공백·줄바꿈이 있어도 글자가 있으면 이름이다. 학교·학년 빈 문자열은 지우기
        assertThat(asAdmin(HttpMethod.PATCH, url, "{\"name\":\" 김 민준\\n\"}")).hasStatus(501);
        assertThat(asAdmin(HttpMethod.PATCH, url, "{\"school\":\"\",\"grade\":\"\"}"))
                .hasStatus(501);
    }

    @Test
    void 학생_변경에_바꿀_값이_없으면_400() {
        // 단어 학습 저장·진도표 수정과 같은 규칙: 아무것도 바꾸지 않는 요청은 성공으로 보이지 않게 막는다
        assertValidation(asAdmin(HttpMethod.PATCH, "/api/v1/admin/students/1", "{}"), "body");
        assertValidation(asAdmin(HttpMethod.PATCH, "/api/v1/admin/students/1", "{\"name\":null}"), "body");
    }
}
