package com.jglearning.jg4seasons.auth.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.MutableClock;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.auth.AuthCookieSupport;
import com.jglearning.jg4seasons.auth.UserRole;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

/** 1.7 GET /me — 학원은 access 쿠키(JWT aca)에서만 정한다. */
@IntegrationTest
@Transactional
class MeTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    AuthCookieSupport cookies;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MutableClock clock;

    long academyId;
    long studentId;

    @BeforeEach
    void setUp() {
        TestData data = new TestData(jdbc);
        academyId = data.academy("테스트 학원");
        studentId = data.student(academyId, "김학생");
    }

    private MvcTestResult me(long userId, long academy) {
        return mvc.get()
                .uri("/api/v1/me")
                .cookie(cookies.studentAccess(userId, academy))
                .exchange();
    }

    @Test
    void access_쿠키로_내_정보를_받는다() {
        MvcTestResult result = me(studentId, academyId);

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.id").asNumber().isEqualTo((int) studentId);
        assertThat(result).bodyJson().extractingPath("$.name").isEqualTo("김학생");
        assertThat(result).bodyJson().extractingPath("$.role").isEqualTo("student");
        assertThat(result).bodyJson().extractingPath("$.academy.id").asNumber().isEqualTo((int) academyId);
        assertThat(result).bodyJson().extractingPath("$.academy.name").isEqualTo("테스트 학원");
    }

    @Test
    void access는_30분까지_되고_그_뒤에는_401이다() {
        Cookie access = cookies.studentAccess(studentId, academyId);

        clock.advance(Duration.ofMinutes(30).minusSeconds(1));
        assertThat(mvc.get().uri("/api/v1/me").cookie(access)).as("29분 59초").hasStatusOk();

        clock.advance(Duration.ofSeconds(2));
        MvcTestResult expired = mvc.get().uri("/api/v1/me").cookie(access).exchange();
        assertThat(expired).as("30분 1초").hasStatus(401);
        assertThat(expired).bodyJson().extractingPath("$.code").isEqualTo("UNAUTHORIZED");
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    @Test
    void 교사와_관리자도_내_정보를_받는다() {
        TestData data = new TestData(jdbc);
        long teacherId = data.staff(academyId, "김교사", "teacher");
        long adminId = data.staff(academyId, "원관리", "admin");

        assertThat(mvc.get().uri("/api/v1/me").cookie(cookies.access(teacherId, UserRole.TEACHER, academyId)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.role")
                .isEqualTo("teacher");
        assertThat(mvc.get().uri("/api/v1/me").cookie(cookies.access(adminId, UserRole.ADMIN, academyId)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.role")
                .isEqualTo("admin");
    }

    @Test
    void 쿠키가_없으면_401_UNAUTHORIZED() {
        MvcTestResult result = mvc.get().uri("/api/v1/me").exchange();

        assertThat(result).hasStatus(401);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("UNAUTHORIZED");
    }

    @Test
    void Authorization_헤더의_Bearer는_읽지_않는다() {
        String jwt = cookies.studentAccess(studentId, academyId).getValue();

        MvcTestResult result = mvc.get()
                .uri("/api/v1/me")
                .header("Authorization", "Bearer " + jwt)
                .exchange();

        assertThat(result).hasStatus(401);
    }

    @Test
    void 비활성_사용자의_access는_401() {
        jdbc.update("update `user` set status = 'inactive' where id = ?", studentId);

        assertThat(me(studentId, academyId)).hasStatus(401);
    }

    @Test
    void 정지된_학원_사용자의_access는_401() {
        jdbc.update("update academy set status = 'suspended' where id = ?", academyId);

        assertThat(me(studentId, academyId)).hasStatus(401);
    }

    @Test
    void JWT의_학원과_사용자의_학원이_다르면_401() {
        long otherAcademy = new TestData(jdbc).academy("다른 학원");

        assertThat(me(studentId, otherAcademy))
                .as("aca를 바꾼 토큰은 서명이 맞아도 사용자를 찾지 못한다")
                .hasStatus(401);
    }
}
