package com.jglearning.jg4seasons.listening.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.MutableClock;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.auth.AuthCookieSupport;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
 * 학생 회차 관문의 사용권 판정(ADR 0031): 2.3·2.4·2.5·3.1이 같은 관문을 지난다. 사용권을 적용하는 학원, 반은 L2-B1, 오늘 = 서울 2026-11-10, 진도표
 * 5회차 11/10. 순서는 소속 반 없음 404 → 사용 기간 밖 403 → 회차 잠금 423.
 */
@IntegrationTest
@Transactional
class LessonLicenseGateTest {

    static final String CODE = "L2-B1-05";
    static final LocalDate TODAY = LocalDate.of(2026, 11, 10);
    /** 서울 2026-11-10 12:00 */
    static final Instant NOON_NOV_10 = Instant.parse("2026-11-10T03:00:00Z");

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
    Map<String, Long> tracks;

    @BeforeEach
    void setUp() {
        clock.set(NOON_NOV_10);
        data = new TestData(jdbc);
        academyId = data.academy("연계 학원");
        data.enforceLicenses(academyId);
        long classId = data.clazz(academyId, "L2-B1", "겨울 그린 A반", TestData.uniqueAccessCode(), "active");
        studentId = data.student(academyId, "김학생");
        data.enroll(classId, studentId);
        data.session(classId, 5, CODE, TODAY, "scheduled");
        data.session(classId, 6, "L2-B1-06", TODAY.plusDays(1), "scheduled");
        tracks = data.lessonContent(CODE, 1, 2, List.of("us_w"));
        data.lessonContent("L2-B1-06", 1, 1, List.of("us_w"));
        data.words(CODE, 3);
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    private void license(LocalDate start, LocalDate end) {
        data.license(academyId, "L2-B1", start, end, 30);
    }

    private Cookie access() {
        return cookies.studentAccess(studentId, academyId);
    }

    private MvcTestResult lesson(String code) {
        return mvc.get().uri("/api/v1/student/lessons/" + code).cookie(access()).exchange();
    }

    private MvcTestResult words() {
        return mvc.get()
                .uri("/api/v1/student/lessons/" + CODE + "/words")
                .cookie(access())
                .exchange();
    }

    private MvcTestResult wordStudy() {
        return mvc.put()
                .uri("/api/v1/student/lessons/" + CODE + "/word-study")
                .cookie(access(), AuthCookieSupport.csrf())
                .header(AuthCookieSupport.CSRF_HEADER, AuthCookieSupport.CSRF_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memKnown\":1}")
                .exchange();
    }

    private MvcTestResult flush() {
        return mvc.post()
                .uri("/api/v1/listening/flush")
                .contentType(MediaType.APPLICATION_JSON)
                .cookie(access(), AuthCookieSupport.csrf())
                .header(AuthCookieSupport.CSRF_HEADER, AuthCookieSupport.CSRF_VALUE)
                .content(FlushTest.body(UUID.randomUUID().toString(), tracks.get("p1:us_w"), FlushTest.seg(0, 1000)))
                .exchange();
    }

    private static void assertInactive(MvcTestResult result) {
        assertThat(result).hasStatus(403);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("LICENSE_INACTIVE");
    }

    private long count(String sql) {
        Long n = jdbc.queryForObject(sql, Long.class);
        return n == null ? 0 : n;
    }

    @Test
    void 기간_안이면_회차를_연다() {
        license(TODAY.minusDays(9), TODAY.plusDays(20));

        assertThat(lesson(CODE)).hasStatusOk();
        assertThat(words()).hasStatusOk();
        assertThat(wordStudy()).hasStatusOk();
        assertThat(flush()).hasStatusOk();
    }

    @Test
    void 만료_뒤면_회차_상세_단어_학습_결과_청취_기록이_모두_403() {
        license(TODAY.minusDays(40), TODAY.minusDays(1));

        assertInactive(lesson(CODE));
        assertInactive(words());
        assertInactive(wordStudy());
        assertInactive(flush());
    }

    @Test
    void 만료_뒤_청취_기록은_저장하지_않는다() {
        license(TODAY.minusDays(40), TODAY.minusDays(1));

        flush();

        assertThat(count("select count(*) from listening_session")).isZero();
        assertThat(count("select count(*) from listening_segment")).isZero();
    }

    @Test
    void 시작_전이면_403() {
        license(TODAY.plusDays(1), TODAY.plusDays(60));

        assertInactive(lesson(CODE));
    }

    @Test
    void 사용권이_없는_권이면_403() {
        assertInactive(lesson(CODE));
    }

    @Test
    void 만료여도_소속_반이_없는_권의_회차는_404() {
        data.lessonContent("L2-B2-01", 1, 1, List.of("us_w"));

        MvcTestResult result = lesson("L2-B2-01");

        assertThat(result).hasStatus(404);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
    }

    @Test
    void 기간_안이어도_내일_회차는_423() {
        license(TODAY.minusDays(9), TODAY.plusDays(20));

        MvcTestResult result = lesson("L2-B1-06");

        assertThat(result).hasStatus(423);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("LESSON_LOCKED");
    }

    @Test
    void 사용_기간_밖이면_내일_회차도_423이_아니라_403() {
        MvcTestResult result = lesson("L2-B1-06");

        assertInactive(result);
    }

    @Test
    void 학원_타임존_자정에_만료된다() {
        license(TODAY.minusDays(9), TODAY);

        clock.set(Instant.parse("2026-11-10T14:59:59Z")); // 서울 11/10 23:59:59 — 종료일 당일
        assertThat(lesson(CODE)).hasStatusOk();

        clock.set(Instant.parse("2026-11-10T15:00:00Z")); // 서울 11/11 00:00:00, UTC는 아직 11/10
        assertInactive(lesson(CODE));
    }

    @Test
    void 사용권을_적용하지_않는_학원은_사용권_없이_연다() {
        jdbc.update("update academy set license_enforced = false where id = ?", academyId);

        assertThat(lesson(CODE)).hasStatusOk();
    }
}
