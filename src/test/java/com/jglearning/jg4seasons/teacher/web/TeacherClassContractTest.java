package com.jglearning.jg4seasons.teacher.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.auth.AuthCookieSupport;
import com.jglearning.jg4seasons.auth.UserRole;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

/** 4.1 담당 반 목록·4.2 청취 현황 매트릭스 계약: 검증(400)과 접근 규칙(401·403). 실제 동작은 TeacherClassListTest·TeacherMatrixTest(이슈 #31). */
@IntegrationTest
@Transactional
class TeacherClassContractTest {

    static final String CLASSES = "/api/v1/teacher/classes";
    static final String MATRIX = "/api/v1/teacher/classes/1/matrix";

    @Autowired
    MockMvcTester mvc;

    @Autowired
    AuthCookieSupport cookies;

    @Autowired
    JdbcTemplate jdbc;

    long academyId;
    long teacherId;
    long adminId;
    long studentId;

    @BeforeEach
    void setUp() {
        TestData data = new TestData(jdbc);
        academyId = data.academy("교사 계약 학원");
        teacherId = data.staff(academyId, "김교사", "teacher");
        adminId = data.staff(academyId, "관리자", "admin");
        studentId = data.student(academyId, "학생");
    }

    private Cookie[] as(long userId, UserRole role) {
        return new Cookie[] {cookies.access(userId, role, academyId), AuthCookieSupport.csrf()};
    }

    private MvcTestResult get(String uri, Cookie[] who) {
        return mvc.get().uri(uri).cookie(who).exchange();
    }

    private MvcTestResult asTeacher(String uri) {
        return get(uri, as(teacherId, UserRole.TEACHER));
    }

    private static void assertValidation(MvcTestResult result, String field) {
        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION");
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo(field);
    }

    @Test
    void 학생은_403_쿠키가_없으면_401() {
        assertThat(get(CLASSES, as(studentId, UserRole.STUDENT))).hasStatus(403);
        assertThat(get(MATRIX, as(studentId, UserRole.STUDENT))).hasStatus(403);
        assertThat(mvc.get().uri(MATRIX).exchange()).hasStatus(401);
    }

    @Test
    void 매트릭스_범위는_둘_다_보내거나_둘_다_뺀다() {
        assertValidation(asTeacher(MATRIX + "?fromSeq=1"), "toSeq");
        assertValidation(asTeacher(MATRIX + "?toSeq=8"), "fromSeq");
        assertValidation(asTeacher(MATRIX + "?fromSeq=9&toSeq=1"), "fromSeq");
    }

    @Test
    void 매트릭스_범위와_반_id가_틀리면_400() {
        assertValidation(asTeacher(MATRIX + "?fromSeq=0&toSeq=8"), "fromSeq");
        assertValidation(asTeacher(MATRIX + "?fromSeq=1&toSeq=100"), "toSeq");
        assertValidation(asTeacher(MATRIX + "?fromSeq=a&toSeq=8"), "fromSeq");
        assertValidation(asTeacher("/api/v1/teacher/classes/0/matrix"), "classId");
    }

    @Test
    void 매트릭스_범위의_경계값은_검증을_통과한다() {
        // 검증을 지나 반 확인까지 간다(이 학원에 없는 반이라 404)
        assertThat(asTeacher(MATRIX + "?fromSeq=5&toSeq=5")).hasStatus(404);
        assertThat(asTeacher(MATRIX + "?fromSeq=1&toSeq=99")).hasStatus(404);
    }
}
