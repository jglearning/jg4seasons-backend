package com.jglearning.jg4seasons.teacher.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.auth.AuthCookieSupport;
import com.jglearning.jg4seasons.auth.UserRole;
import jakarta.servlet.http.Cookie;
import java.util.List;
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
 * 4.3~4.8 교사 학생·회차 상세, 진도표 조회·수정·휴강, 개인 코드 재발급 계약. 4.6·4.7은 구현 전(501), 4.3·4.4(이슈 #39)·4.5(이슈 #41)·4.8(이슈 #29)은 구현됨 — 실제 동작은
 * TeacherStudentDetailTest·TeacherLessonDetailTest·TeacherPersonalCodeTest.
 */
@IntegrationTest
@Transactional
class TeacherDetailContractTest {

    record Call(HttpMethod method, String path, String body) {}

    static final String SESSION = "/api/v1/teacher/classes/1/sessions/3";

    static final List<Call> CALLS = List.of(
            new Call(HttpMethod.GET, "/api/v1/teacher/classes/1/students/2", null),
            new Call(HttpMethod.GET, "/api/v1/teacher/classes/1/lessons/L2-B1-05", null),
            new Call(HttpMethod.GET, "/api/v1/teacher/classes/1/sessions", null),
            new Call(
                    HttpMethod.PATCH,
                    SESSION,
                    "{\"scheduledDate\":\"2026-12-26\",\"note\":\"보강\",\"shiftFollowing\":true}"),
            new Call(HttpMethod.POST, SESSION + "/cancel", "{\"note\":\"내신 휴강\",\"shiftFollowing\":true}"),
            new Call(HttpMethod.POST, "/api/v1/teacher/students/2/personal-code/regenerate", null));

    /** 구현 전(4.6·4.7). 4.3·4.4(#39)·4.5(#41)·4.8은 구현됨 — 경로의 반·학생이 이 학원에 없어 404 */
    static final List<Call> NOT_YET = CALLS.subList(3, 5);

    @Autowired
    MockMvcTester mvc;

    @Autowired
    AuthCookieSupport cookies;

    @Autowired
    JdbcTemplate jdbc;

    long academyId;
    long teacherId;
    long studentId;

    @BeforeEach
    void setUp() {
        TestData data = new TestData(jdbc);
        academyId = data.academy("교사 상세 계약 학원");
        teacherId = data.staff(academyId, "김교사", "teacher");
        studentId = data.student(academyId, "학생");
    }

    private Cookie[] as(long userId, UserRole role) {
        return new Cookie[] {cookies.access(userId, role, academyId), AuthCookieSupport.csrf()};
    }

    private MvcTestResult call(Call c, Cookie[] who, boolean withCsrf) {
        var request = mvc.method(c.method()).uri(c.path()).cookie(who);
        if (withCsrf) {
            request = request.header(AuthCookieSupport.CSRF_HEADER, AuthCookieSupport.CSRF_VALUE);
        }
        if (c.body() != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(c.body());
        }
        return request.exchange();
    }

    private MvcTestResult asTeacher(HttpMethod method, String path, String body) {
        return call(new Call(method, path, body), as(teacherId, UserRole.TEACHER), true);
    }

    private static void assertValidation(MvcTestResult result, String field) {
        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION");
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo(field);
    }

    @Test
    void 교사의_올바른_요청은_501_NOT_IMPLEMENTED() {
        for (Call c : NOT_YET) {
            MvcTestResult result = call(c, as(teacherId, UserRole.TEACHER), true);

            assertThat(result).as(c.method() + " " + c.path()).hasStatus(501);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("NOT_IMPLEMENTED");
        }
    }

    @Test
    void 학생은_403() {
        for (Call c : CALLS) {
            assertThat(call(c, as(studentId, UserRole.STUDENT), true))
                    .as(c.method() + " " + c.path())
                    .hasStatus(403);
        }
    }

    @Test
    void 상태를_바꾸는_요청에_X_CSRF가_없으면_403() {
        for (Call c : CALLS) {
            if (c.method() != HttpMethod.GET) {
                assertThat(call(c, as(teacherId, UserRole.TEACHER), false))
                        .as(c.method() + " " + c.path())
                        .hasStatus(403);
            }
        }
    }

    @Test
    void 진도표_수정_요청이_틀리면_400() {
        // 바뀌는 것이 없는 요청은 받지 않는다
        assertValidation(asTeacher(HttpMethod.PATCH, SESSION, "{}"), "body");
        assertValidation(asTeacher(HttpMethod.PATCH, SESSION, "{\"note\":\"" + "가".repeat(101) + "\"}"), "note");
        assertThat(asTeacher(HttpMethod.PATCH, SESSION, "{\"scheduledDate\":\"2026-13-01\"}"))
                .hasStatus(400);
        // 회차 순서 바꾸기는 1차에 없다 — lessonCode는 명세에 없는 칸
        assertValidation(asTeacher(HttpMethod.PATCH, SESSION, "{\"lessonCode\":\"L2-B1-05\"}"), "lessonCode");
    }

    @Test
    void 휴강_요청이_틀리면_400() {
        assertValidation(asTeacher(HttpMethod.POST, SESSION + "/cancel", "{}"), "shiftFollowing");
        assertValidation(
                asTeacher(
                        HttpMethod.POST,
                        SESSION + "/cancel",
                        "{\"note\":\"" + "가".repeat(101) + "\",\"shiftFollowing\":true}"),
                "note");
    }

    @Test
    void 경로_값이_틀리면_400() {
        assertValidation(
                asTeacher(HttpMethod.POST, "/api/v1/teacher/classes/1/sessions/0/cancel", "{\"shiftFollowing\":true}"),
                "seq");
        assertValidation(
                asTeacher(HttpMethod.PATCH, "/api/v1/teacher/classes/1/sessions/100", "{\"note\":\"a\"}"), "seq");
        assertValidation(asTeacher(HttpMethod.GET, "/api/v1/teacher/classes/1/lessons/L2-B1-05!", null), "lessonCode");
        assertValidation(asTeacher(HttpMethod.GET, "/api/v1/teacher/classes/1/students/0", null), "studentId");
        assertValidation(
                asTeacher(HttpMethod.POST, "/api/v1/teacher/students/0/personal-code/regenerate", null), "studentId");
        assertValidation(asTeacher(HttpMethod.GET, "/api/v1/teacher/classes/0/sessions", null), "classId");
    }

    @Test
    void 관리자도_교사_경로를_쓴다() {
        // 역할 계층(admin ⊃ teacher) — 학원관리자가 T-04~T-06을 쓴다
        long adminId = new TestData(jdbc).staff(academyId, "관리자", "admin");

        for (Call c : NOT_YET) {
            assertThat(call(c, as(adminId, UserRole.ADMIN), true))
                    .as(c.method() + " " + c.path())
                    .hasStatus(501);
        }
        // 4.3·4.4·4.5·4.8은 역할을 통과해 반·학생 확인까지 간다(이 학원에 없는 id라 404)
        assertThat(call(CALLS.get(0), as(adminId, UserRole.ADMIN), true)).hasStatus(404);
        assertThat(call(CALLS.get(1), as(adminId, UserRole.ADMIN), true)).hasStatus(404);
        assertThat(call(CALLS.get(2), as(adminId, UserRole.ADMIN), true)).hasStatus(404);
        assertThat(call(CALLS.get(5), as(adminId, UserRole.ADMIN), true)).hasStatus(404);
    }

    @Test
    void 쿠키가_없으면_401() {
        assertThat(mvc.get().uri("/api/v1/teacher/classes/1/sessions").exchange())
                .hasStatus(401);
    }

    @Test
    void 진도표_수정의_경계() {
        // 뒤를 밀지 여부만으로는 바뀌는 것이 없다
        assertValidation(asTeacher(HttpMethod.PATCH, SESSION, "{\"shiftFollowing\":true}"), "body");
        // 메모 빈 문자열 = 지우기, 경계값(seq 99·메모 100자)은 통과
        assertThat(asTeacher(HttpMethod.PATCH, SESSION, "{\"note\":\"\"}")).hasStatus(501);
        assertThat(asTeacher(
                        HttpMethod.PATCH,
                        "/api/v1/teacher/classes/1/sessions/99",
                        "{\"note\":\"" + "가".repeat(100) + "\"}"))
                .hasStatus(501);
    }
}
