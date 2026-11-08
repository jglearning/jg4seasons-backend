package com.jglearning.jg4seasons.words.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.auth.AuthCookieSupport;
import com.jglearning.jg4seasons.auth.UserRole;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

/** 2.4 단어·2.5 학습 결과 계약(구현 전 501). 검증(400)과 접근 규칙(401·403)은 지금도 동작한다. */
@IntegrationTest
@Transactional
class StudentWordContractTest {

    static final String WORDS = "/api/v1/student/lessons/L2-B1-05/words";
    static final String STUDY = "/api/v1/student/lessons/L2-B1-05/word-study";

    @Autowired
    MockMvcTester mvc;

    @Autowired
    AuthCookieSupport cookies;

    @Autowired
    JdbcTemplate jdbc;

    long academyId;
    long studentId;
    long teacherId;

    @BeforeEach
    void setUp() {
        TestData data = new TestData(jdbc);
        academyId = data.academy("단어 계약 학원");
        studentId = data.student(academyId, "학생");
        teacherId = data.staff(academyId, "김교사", "teacher");
    }

    private Cookie[] as(long userId, UserRole role) {
        return new Cookie[] {cookies.access(userId, role, academyId), AuthCookieSupport.csrf()};
    }

    private MvcTestResult put(Cookie[] who, String path, String body, boolean withCsrf) {
        var request = mvc.put()
                .uri(path)
                .cookie(who)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
        if (withCsrf) {
            request = request.header(AuthCookieSupport.CSRF_HEADER, AuthCookieSupport.CSRF_VALUE);
        }
        return request.exchange();
    }

    private MvcTestResult study(String body) {
        return put(as(studentId, UserRole.STUDENT), STUDY, body, true);
    }

    private static void assertValidation(MvcTestResult result, String field) {
        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION");
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo(field);
    }

    @Test
    void 학생의_올바른_요청은_501_NOT_IMPLEMENTED() {
        MvcTestResult words =
                mvc.get().uri(WORDS).cookie(as(studentId, UserRole.STUDENT)).exchange();
        MvcTestResult saved = study("{\"cardsViewed\":57,\"memKnown\":40}");

        for (MvcTestResult result : new MvcTestResult[] {words, saved}) {
            assertThat(result).hasStatus(501);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("NOT_IMPLEMENTED");
        }
    }

    @Test
    void 교사는_403_쿠키가_없으면_401() {
        assertThat(mvc.get().uri(WORDS).cookie(as(teacherId, UserRole.TEACHER)).exchange())
                .hasStatus(403);
        assertThat(put(as(teacherId, UserRole.TEACHER), STUDY, "{\"memKnown\":1}", true))
                .hasStatus(403);
        assertThat(mvc.get().uri(WORDS).exchange()).hasStatus(401);
    }

    @Test
    void 학습_결과_저장에_X_CSRF가_없으면_403() {
        assertThat(put(as(studentId, UserRole.STUDENT), STUDY, "{\"memKnown\":1}", false))
                .hasStatus(403);
    }

    @Test
    void 회차_코드_형식이_틀리면_400() {
        MvcTestResult result = mvc.get()
                .uri("/api/v1/student/lessons/L2-B1-05!/words")
                .cookie(as(studentId, UserRole.STUDENT))
                .exchange();

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION");
    }

    @Test
    void 학습_결과가_틀리면_400() {
        // 바뀌는 것이 없는 요청은 받지 않는다
        assertValidation(study("{}"), "body");
        assertValidation(study("{\"memKnown\":-1}"), "memKnown");
        // word_study 칸이 TINYINT라 127까지
        assertValidation(study("{\"spellCorrect\":128}"), "spellCorrect");
        // 숫자 칸에 문자열은 받지 않는다(엄격 JSON)
        assertThat(study("{\"memKnown\":\"3\"}")).hasStatus(400);
    }

    @Test
    void 상한값은_통과한다() {
        assertThat(study("{\"cardsViewed\":127,\"memKnown\":0,\"recallCorrect\":127,\"spellCorrect\":127}"))
                .hasStatus(501);
    }

    @Test
    void 관리자는_학생_단어_경로에서_403() {
        // 역할 계층에 STUDENT가 들어가면 관리자가 학생 API를 쓰게 된다 — 그러지 않음을 고정
        long adminId = new TestData(jdbc).staff(academyId, "관리자", "admin");

        assertThat(mvc.get().uri(WORDS).cookie(as(adminId, UserRole.ADMIN)).exchange())
                .hasStatus(403);
    }
}
