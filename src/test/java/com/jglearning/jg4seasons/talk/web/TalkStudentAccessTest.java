package com.jglearning.jg4seasons.talk.web;

import static com.jglearning.jg4seasons.talk.web.TalkTestSupport.allow;
import static com.jglearning.jg4seasons.talk.web.TalkTestSupport.assertProblem;
import static com.jglearning.jg4seasons.talk.web.TalkTestSupport.config;
import static com.jglearning.jg4seasons.talk.web.TalkTestSupport.create;
import static com.jglearning.jg4seasons.talk.web.TalkTestSupport.features;
import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.MutableClock;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.auth.AuthCookieSupport;
import com.jglearning.jg4seasons.talk.realtime.FakeRealtimeSessionIssuer;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.transaction.annotation.Transactional;

/** 학생 허용(ADR 0040): 학원 스위치 → 학생 허용 → 구독 순서. 꺼진 학생은 숨김(404 FEATURE_DISABLED) */
@IntegrationTest
@Transactional
class TalkStudentAccessTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    AuthCookieSupport cookies;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MutableClock clock;

    @Autowired
    FakeRealtimeSessionIssuer issuer;

    TestData data;
    long academyId;
    long studentId;

    @BeforeEach
    void setUp() {
        clock.set(Instant.parse("2026-11-26T03:00:00Z")); // 서울 11/26 12:00
        issuer.reset();
        data = new TestData(jdbc);
        academyId = data.academy("허용 학원");
        features(jdbc, academyId, true, true);
        long classId = data.clazz(academyId, "L2-B1", "그린 반", TestData.uniqueAccessCode(), "active");
        studentId = data.student(academyId, "허용 학생");
        data.enroll(classId, studentId);
        data.session(classId, 1, "L2-B1-03", LocalDate.of(2026, 11, 26), "scheduled");
    }

    @AfterEach
    void tearDown() {
        clock.reset();
        issuer.reset();
    }

    @Test
    void 학원이_켜져도_허용되지_않은_학생은_설정_조회가_404_FEATURE_DISABLED() {
        assertProblem(config(mvc, student()), HttpStatus.NOT_FOUND, "FEATURE_DISABLED");
        assertProblem(
                create(mvc, student(), UUID.randomUUID().toString(), "day"), HttpStatus.NOT_FOUND, "FEATURE_DISABLED");
    }

    @Test
    void 허용된_학생은_설정_조회가_200() {
        allow(jdbc, academyId, studentId);

        assertThat(config(mvc, student())).hasStatusOk();
    }

    @Test
    void 허용됐다가_꺼진_학생은_세션_생성이_404_FEATURE_DISABLED() {
        allow(jdbc, academyId, studentId);
        jdbc.update("update talk_student_access set enabled = false where student_id = ?", studentId);

        assertProblem(
                create(mvc, student(), UUID.randomUUID().toString(), "day"), HttpStatus.NOT_FOUND, "FEATURE_DISABLED");
    }

    @Test
    void 학원이_꺼지면_허용된_학생도_404_FEATURE_DISABLED() {
        allow(jdbc, academyId, studentId);
        features(jdbc, academyId, false, false);

        assertProblem(config(mvc, student()), HttpStatus.NOT_FOUND, "FEATURE_DISABLED");
    }

    @Test
    void 허용된_학생이라도_구독이_없으면_세션_생성은_403_SUBSCRIPTION_REQUIRED() {
        allow(jdbc, academyId, studentId);
        features(jdbc, academyId, true, false);

        assertProblem(
                create(mvc, student(), UUID.randomUUID().toString(), "day"),
                HttpStatus.FORBIDDEN,
                "SUBSCRIPTION_REQUIRED");
    }

    private Cookie student() {
        return cookies.studentAccess(studentId, academyId);
    }
}
