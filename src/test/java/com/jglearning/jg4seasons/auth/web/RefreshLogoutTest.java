package com.jglearning.jg4seasons.auth.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.MutableClock;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.auth.AuthCookieSupport;
import com.jglearning.jg4seasons.auth.AuthIdentity;
import com.jglearning.jg4seasons.auth.AuthIdentityRepository;
import com.jglearning.jg4seasons.auth.PersonalCodeHasher;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

/** 1.5 POST /auth/refresh · 1.6 POST /auth/logout. */
@IntegrationTest
@Transactional
class RefreshLogoutTest {

    static final AtomicInteger IP_SEQ = new AtomicInteger(1);

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PersonalCodeHasher hasher;

    @Autowired
    AuthIdentityRepository identities;

    @Autowired
    MutableClock clock;

    long academyId;
    long studentId;
    Cookie[] loginCookies;

    @BeforeEach
    void setUp() {
        clock.set(Clock.systemUTC().instant().truncatedTo(ChronoUnit.SECONDS));
        TestData data = new TestData(jdbc);
        academyId = data.academy("테스트 학원");
        String accessCode = TestData.uniqueAccessCode();
        long classId = data.clazz(academyId, "L2-B1", "A반", accessCode, "active");
        studentId = data.student(academyId, "김학생");
        data.enroll(classId, studentId);
        identities.save(AuthIdentity.personalCode(studentId, hasher.hash(academyId, "3817"), clock.instant()));
        MvcTestResult login = mvc.post()
                .uri("/api/v1/auth/student/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + accessCode + "\",\"personalCode\":\"3817\"}")
                .with(request -> {
                    request.setRemoteAddr("192.0.2." + IP_SEQ.getAndIncrement());
                    return request;
                })
                .exchange();
        assertThat(login).hasStatusOk();
        loginCookies = login.getResponse().getCookies();
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    static Optional<Cookie> cookie(Cookie[] cookies, String name) {
        return Arrays.stream(cookies).filter(c -> c.getName().equals(name)).findFirst();
    }

    static Cookie refreshOf(Cookie[] cookies) {
        return cookie(cookies, "refresh").orElseThrow();
    }

    private MvcTestResult refresh(Cookie... sent) {
        if (sent.length == 0) {
            return mvc.post().uri("/api/v1/auth/refresh").exchange();
        }
        return mvc.post().uri("/api/v1/auth/refresh").cookie(sent).exchange();
    }

    static List<String> setCookies(MvcTestResult result) {
        return result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
    }

    @Test
    void refresh하면_새_쿠키_3개와_내_정보() {
        MvcTestResult result = refresh(refreshOf(loginCookies));

        assertThat(result).hasStatusOk();
        assertThat(setCookies(result)).hasSize(3);
        assertThat(result).bodyJson().extractingPath("$.id").asNumber().isEqualTo((int) studentId);
        assertThat(result).bodyJson().extractingPath("$.role").isEqualTo("student");
        assertThat(result).bodyJson().extractingPath("$.academy.name").isEqualTo("테스트 학원");
        assertThat(refreshOf(result.getResponse().getCookies()).getValue())
                .isNotEqualTo(refreshOf(loginCookies).getValue());
    }

    @Test
    void 새_refresh로_다시_refresh되고_새_access로_me가_된다() {
        Cookie[] rotated = refresh(refreshOf(loginCookies)).getResponse().getCookies();

        assertThat(refresh(refreshOf(rotated))).hasStatusOk();
        assertThat(mvc.get().uri("/api/v1/me").cookie(cookie(rotated, "access").orElseThrow()))
                .hasStatusOk();
    }

    @Test
    void 옛_refresh를_10초_안에_다시_보내면_200이고_쿠키는_그대로() {
        refresh(refreshOf(loginCookies));
        clock.advance(Duration.ofSeconds(10));

        MvcTestResult again = refresh(refreshOf(loginCookies));

        assertThat(again).hasStatusOk();
        assertThat(setCookies(again)).as("다른 탭이 받은 새 쿠키를 같이 쓴다").isEmpty();
        assertThat(again).bodyJson().extractingPath("$.id").asNumber().isEqualTo((int) studentId);
    }

    @Test
    void 옛_refresh를_유예_뒤에_보내면_401이고_새_refresh도_끊긴다() {
        Cookie[] rotated = refresh(refreshOf(loginCookies)).getResponse().getCookies();
        clock.advance(Duration.ofSeconds(11));

        MvcTestResult reused = refresh(refreshOf(loginCookies));

        assertThat(reused).hasStatus(401);
        assertThat(reused).bodyJson().extractingPath("$.code").isEqualTo("UNAUTHORIZED");
        assertThat(refresh(refreshOf(rotated))).as("빼낸 쪽·학생 쪽 모두 끊김").hasStatus(401);
    }

    @Test
    void 거부되면_쿠키_3개를_만료시킨다() {
        clock.advance(Duration.ofDays(91));

        MvcTestResult result = refresh(refreshOf(loginCookies));

        assertThat(result).hasStatus(401);
        assertThat(setCookies(result)).hasSize(3).allMatch(c -> c.contains("Max-Age=0"));
    }

    @Test
    void refresh_쿠키가_없으면_400이_아니라_401_UNAUTHORIZED() {
        MvcTestResult result = refresh();

        assertThat(result).hasStatus(401).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("UNAUTHORIZED");
        assertThat(setCookies(result)).hasSize(3).allMatch(c -> c.contains("Max-Age=0"));
    }

    @Test
    void refresh_쿠키_없이_로그아웃해도_204와_쿠키_만료() {
        Cookie[] withoutRefresh = Arrays.stream(loginCookies)
                .filter(c -> !c.getName().equals("refresh"))
                .toArray(Cookie[]::new);

        MvcTestResult logout = mvc.post()
                .uri("/api/v1/auth/logout")
                .cookie(withoutRefresh)
                .header(
                        AuthCookieSupport.CSRF_HEADER,
                        cookie(loginCookies, "csrf").orElseThrow().getValue())
                .exchange();

        assertThat(logout).hasStatus(204);
        assertThat(setCookies(logout)).hasSize(3).allMatch(c -> c.contains("Max-Age=0"));
    }

    @Test
    void 만료된_access_쿠키와_함께_보내도_refresh된다() {
        clock.advance(Duration.ofMinutes(31));

        assertThat(refresh(loginCookies)).hasStatusOk();
    }

    @Test
    void 퇴원한_학생의_refresh는_401() {
        jdbc.update("update `user` set status = 'inactive' where id = ?", studentId);

        assertThat(refresh(refreshOf(loginCookies))).hasStatus(401);
    }

    @Test
    void 로그아웃하면_204와_쿠키_만료_그리고_refresh_폐기() {
        MvcTestResult logout = mvc.post()
                .uri("/api/v1/auth/logout")
                .cookie(loginCookies)
                .header(
                        AuthCookieSupport.CSRF_HEADER,
                        cookie(loginCookies, "csrf").orElseThrow().getValue())
                .exchange();

        assertThat(logout).hasStatus(204);
        assertThat(setCookies(logout)).hasSize(3).allMatch(c -> c.contains("Max-Age=0"));
        assertThat(refresh(refreshOf(loginCookies))).as("유예 안이라도 바로 401").hasStatus(401);
    }

    @Test
    void X_CSRF_없이_로그아웃하면_403() {
        MvcTestResult logout =
                mvc.post().uri("/api/v1/auth/logout").cookie(loginCookies).exchange();

        assertThat(logout).hasStatus(403);
        assertThat(refresh(refreshOf(loginCookies))).as("refresh는 살아 있다").hasStatusOk();
    }
}
