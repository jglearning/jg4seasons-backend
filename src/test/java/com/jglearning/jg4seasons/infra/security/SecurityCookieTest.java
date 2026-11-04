package com.jglearning.jg4seasons.infra.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.MutableClock;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.auth.AuthCookieSupport;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

/** 쿠키 JWT 인증·X-CSRF 검사·역할(메서드 보안). */
@IntegrationTest
@Transactional
class SecurityCookieTest {

    static final String STUDENT_WRITE = "/test-support/security/student-write";

    @Autowired
    MockMvcTester mvc;

    @Autowired
    AuthCookieSupport cookies;

    @Autowired
    MutableClock clock;

    @Autowired
    JdbcTemplate jdbc;

    String accessCode;
    long academyId;
    long studentId;

    @BeforeEach
    void setUp() {
        TestData data = new TestData(jdbc);
        accessCode = TestData.uniqueAccessCode();
        academyId = data.academy("테스트 학원");
        data.clazz(academyId, "L2-B1", "A반", accessCode, "active");
        // 실제 학생: 토큰이 유효하면 /me가 200이어야 만료·깨짐 판정을 따로 볼 수 있다
        studentId = data.student(academyId, "학생");
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    private MvcTestResult studentWrite(Cookie... sent) {
        return mvc.post().uri(STUDENT_WRITE).cookie(sent).exchange();
    }

    @Test
    void X_CSRF가_쿠키와_같으면_통과() {
        MvcTestResult result = mvc.post()
                .uri(STUDENT_WRITE)
                .cookie(cookies.studentAccess(1L, 1L), AuthCookieSupport.csrf())
                .header(AuthCookieSupport.CSRF_HEADER, AuthCookieSupport.CSRF_VALUE)
                .exchange();

        assertThat(result).hasStatusOk();
    }

    @Test
    void 상태_변경_요청에_X_CSRF가_없으면_403_FORBIDDEN() {
        MvcTestResult result = studentWrite(cookies.studentAccess(1L, 1L), AuthCookieSupport.csrf());

        assertThat(result).hasStatus(403).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("FORBIDDEN");
    }

    @Test
    void X_CSRF가_쿠키와_다르면_403() {
        MvcTestResult result = mvc.post()
                .uri(STUDENT_WRITE)
                .cookie(cookies.studentAccess(1L, 1L), AuthCookieSupport.csrf())
                .header(AuthCookieSupport.CSRF_HEADER, "other-value")
                .exchange();

        assertThat(result).hasStatus(403);
    }

    @Test
    void csrf_쿠키가_없으면_헤더가_있어도_403() {
        MvcTestResult result = mvc.post()
                .uri(STUDENT_WRITE)
                .cookie(cookies.studentAccess(1L, 1L))
                .header(AuthCookieSupport.CSRF_HEADER, AuthCookieSupport.CSRF_VALUE)
                .exchange();

        assertThat(result).hasStatus(403);
    }

    @Test
    void 학생이_교사_전용_메서드를_부르면_403() {
        MvcTestResult result = mvc.get()
                .uri("/test-support/security/teacher-only")
                .cookie(cookies.studentAccess(1L, 1L))
                .exchange();

        assertThat(result).hasStatus(403);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("FORBIDDEN");
    }

    @Test
    void 쿠키가_없으면_역할_메서드는_401() {
        MvcTestResult result =
                mvc.get().uri("/test-support/security/teacher-only").exchange();

        assertThat(result).hasStatus(401);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("UNAUTHORIZED");
    }

    @Test
    void 만료된_access는_401_UNAUTHORIZED() {
        Cookie access = cookies.studentAccess(studentId, academyId);
        assertThat(mvc.get().uri("/api/v1/me").cookie(access)).as("만료 전").hasStatusOk();
        clock.advance(Duration.ofMinutes(31));

        MvcTestResult result = mvc.get().uri("/api/v1/me").cookie(access).exchange();

        assertThat(result).hasStatus(401).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("UNAUTHORIZED");
    }

    @Test
    void 만료된_access_쿠키가_있어도_auth_경로는_필터에서_막히지_않는다() {
        Cookie expired = cookies.studentAccess(1L, 1L);
        clock.advance(Duration.ofMinutes(31));

        MvcTestResult result = mvc.post()
                .uri("/api/v1/auth/code/verify")
                .cookie(expired)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + accessCode + "\"}")
                .with(request -> {
                    request.setRemoteAddr("198.51.100.201");
                    return request;
                })
                .exchange();

        assertThat(result).hasStatusOk();
    }

    @Test
    void 깨진_access_쿠키가_있어도_auth_경로는_통과한다() {
        MvcTestResult result = mvc.post()
                .uri("/api/v1/auth/code/verify")
                .cookie(new Cookie("access", "not-a-jwt"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + accessCode + "\"}")
                .with(request -> {
                    request.setRemoteAddr("198.51.100.202");
                    return request;
                })
                .exchange();

        assertThat(result).hasStatusOk();
    }

    @Test
    void 깨진_access_쿠키로_보호된_경로는_401() {
        assertThat(mvc.get().uri("/api/v1/me").cookie(cookies.studentAccess(studentId, academyId)))
                .as("정상 토큰은 200")
                .hasStatusOk();
        MvcTestResult result = mvc.get()
                .uri("/api/v1/me")
                .cookie(new Cookie("access", "not-a-jwt"))
                .exchange();

        assertThat(result).hasStatus(401);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("UNAUTHORIZED");
    }
}
