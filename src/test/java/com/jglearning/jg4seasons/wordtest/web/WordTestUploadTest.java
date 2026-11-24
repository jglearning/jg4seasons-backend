package com.jglearning.jg4seasons.wordtest.web;

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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * D-1 POST /word-tests/uploads — 단어시험 사진 업로드 주소. 검사 순서: 로그인 → 학원 켜짐 → 회차 → 열림 → 형식. 오늘 = 서울 2026-11-26, 반은 L2-B1, 1회차 오늘·2회차
 * 내일. 사진 저장소는 테스트 메모리 구현
 */
@IntegrationTest
@Transactional
class WordTestUploadTest {

    static final String TODAY = "L2-B1-01";
    static final String TOMORROW = "L2-B1-02";
    static final Instant NOON = Instant.parse("2026-11-26T03:00:00Z");
    static final String URL = "/api/v1/word-tests/uploads";

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
    long studentId;

    @BeforeEach
    void setUp() {
        clock.set(NOON);
        data = new TestData(jdbc);
        academyId = data.academy("단어시험 학원");
        enableWordTest(academyId);
        long classId = data.clazz(academyId, "L2-B1", "겨울 그린 A반", TestData.uniqueAccessCode(), "active");
        studentId = data.student(academyId, "김학생");
        data.enroll(classId, studentId);
        data.session(classId, 1, TODAY, LocalDate.of(2026, 11, 26), "scheduled");
        data.session(classId, 2, TOMORROW, LocalDate.of(2026, 11, 27), "scheduled");
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    @Test
    void 열린_회차면_업로드_주소와_내_경로의_키를_준다() {
        MvcTestResult result = upload(student(), TODAY, "image/jpeg");

        assertThat(result).hasStatusOk();
        var json = assertThat(result).bodyJson();
        json.extractingPath("$.imageKey")
                .asString()
                .matches("^homework/" + academyId + "/" + studentId
                        + "/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.jpg$");
        json.extractingPath("$.uploadUrl").asString().startsWith("https://photos.test/homework/");
        json.extractingPath("$.headers['Content-Type']").isEqualTo("image/jpeg");
        json.extractingPath("$.expiresAt").isEqualTo("2026-11-26T03:05:00Z");
    }

    @Test
    void PNG면_png_키() {
        MvcTestResult result = upload(student(), TODAY, "image/png");

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.imageKey").asString().endsWith(".png");
    }

    @Test
    void JPEG_PNG가_아니면_400_INVALID_IMAGE() {
        assertProblem(upload(student(), TODAY, "image/heic"), HttpStatus.BAD_REQUEST, "INVALID_IMAGE");
        assertProblem(upload(student(), TODAY, "IMAGE/JPEG"), HttpStatus.BAD_REQUEST, "INVALID_IMAGE");
    }

    @Test
    void 기능이_꺼진_학원은_404_FEATURE_DISABLED() {
        jdbc.update("update academy set settings = '{\"timezone\":\"Asia/Seoul\"}' where id = ?", academyId);

        assertProblem(upload(student(), TODAY, "image/jpeg"), HttpStatus.NOT_FOUND, "FEATURE_DISABLED");
    }

    @Test
    void 없는_회차는_404_NOT_FOUND() {
        assertProblem(upload(student(), "L2-B1-99", "image/jpeg"), HttpStatus.NOT_FOUND, "NOT_FOUND");
    }

    @Test
    void 수업일_전_회차는_423_LESSON_LOCKED() {
        assertProblem(upload(student(), TOMORROW, "image/jpeg"), HttpStatus.LOCKED, "LESSON_LOCKED");
    }

    @Test
    void 로그인하지_않으면_401이고_기능_꺼짐보다_먼저() {
        jdbc.update("update academy set settings = null where id = ?", academyId);

        assertThat(mvc.post()
                        .uri(URL)
                        .cookie(AuthCookieSupport.csrf())
                        .header(AuthCookieSupport.CSRF_HEADER, AuthCookieSupport.CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(TODAY, "image/jpeg"))
                        .exchange())
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void 교사는_403() {
        long teacher = data.staff(academyId, "교사", "teacher");

        assertThat(upload(cookies.access(teacher, UserRole.TEACHER, academyId), TODAY, "image/jpeg"))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void CSRF_헤더가_없으면_403() {
        assertThat(mvc.post()
                        .uri(URL)
                        .cookie(student(), AuthCookieSupport.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(TODAY, "image/jpeg"))
                        .exchange())
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void 기능이_꺼져도_기존_회차_상세는_그대로() {
        jdbc.update("update academy set settings = null where id = ?", academyId);

        assertThat(mvc.get()
                        .uri("/api/v1/student/lessons/" + TODAY)
                        .cookie(student())
                        .exchange())
                .hasStatusOk();
    }

    static void enableWordTest(JdbcTemplate jdbc, long academyId) {
        jdbc.update(
                "update academy set settings = '{\"timezone\":\"Asia/Seoul\",\"features\":{\"wordTest\":true}}'"
                        + " where id = ?",
                academyId);
    }

    private void enableWordTest(long academyId) {
        enableWordTest(jdbc, academyId);
    }

    private Cookie student() {
        return cookies.studentAccess(studentId, academyId);
    }

    private MvcTestResult upload(Cookie access, String lessonCode, String contentType) {
        return mvc.post()
                .uri(URL)
                .cookie(access, AuthCookieSupport.csrf())
                .header(AuthCookieSupport.CSRF_HEADER, AuthCookieSupport.CSRF_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(lessonCode, contentType))
                .exchange();
    }

    private static String body(String lessonCode, String contentType) {
        return "{\"lessonCode\":\"" + lessonCode + "\",\"contentType\":\"" + contentType + "\"}";
    }

    private static void assertProblem(MvcTestResult result, HttpStatus status, String code) {
        assertThat(result).hasStatus(status);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo(code);
    }
}
