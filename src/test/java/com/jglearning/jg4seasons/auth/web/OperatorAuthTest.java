package com.jglearning.jg4seasons.auth.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.MutableClock;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.auth.AuthCookieSupport;
import com.jglearning.jg4seasons.auth.AuthIdentity;
import com.jglearning.jg4seasons.auth.AuthIdentityRepository;
import com.jglearning.jg4seasons.auth.UserRole;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영자(superadmin, 학원 없음) 인증(ADR 0032): 1.4 로그인 → access에 aca 없음, refresh·/me는 학원 null. 학원 API(교사·관리자·학생)는 역할
 * 계층 밖이라 403.
 */
@IntegrationTest
@Transactional
class OperatorAuthTest {

    static final String PASSWORD = "operator-pass-1";
    static final AtomicInteger IP_SEQ = new AtomicInteger(1);

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    AuthIdentityRepository identities;

    @Autowired
    AuthCookieSupport cookies;

    @Autowired
    MutableClock clock;

    TestData data;
    long operatorId;
    String loginId;
    String ip;

    @BeforeEach
    void setUp() {
        clock.set(Clock.systemUTC().instant().truncatedTo(ChronoUnit.SECONDS));
        data = new TestData(jdbc);
        operatorId = data.operator("운영자");
        loginId = "operator" + operatorId;
        identities.save(AuthIdentity.password(operatorId, loginId, encoder.encode(PASSWORD), clock.instant()));
        ip = "198.18.1." + IP_SEQ.getAndIncrement();
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    private MvcTestResult login(String id, String password) {
        return mvc.post()
                .uri("/api/v1/auth/staff/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginId\":\"" + id + "\",\"password\":\"" + password + "\"}")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .exchange();
    }

    private static Cookie cookie(MvcTestResult result, String name) {
        Cookie cookie = result.getResponse().getCookie(name);
        assertThat(cookie).as(name + " 쿠키").isNotNull();
        return cookie;
    }

    @Test
    void 운영자는_아이디_비밀번호로_로그인하고_학원은_없다() {
        MvcTestResult result = login(loginId, PASSWORD);

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.user.role").isEqualTo("superadmin");
        assertThat(result).bodyJson().extractingPath("$.user.academyId").isNull();
        assertThat(jdbc.queryForObject(
                        "select last_login_at is not null from `user` where id = ?", Boolean.class, operatorId))
                .as("마지막 로그인 기록")
                .isTrue();
    }

    @Test
    void 운영자의_me와_refresh는_학원이_null() {
        MvcTestResult loggedIn = login(loginId, PASSWORD);

        MvcTestResult me =
                mvc.get().uri("/api/v1/me").cookie(cookie(loggedIn, "access")).exchange();
        assertThat(me).hasStatusOk();
        assertThat(me).bodyJson().extractingPath("$.role").isEqualTo("superadmin");
        assertThat(me).bodyJson().extractingPath("$.academy").isNull();

        MvcTestResult refreshed = mvc.post()
                .uri("/api/v1/auth/refresh")
                .cookie(cookie(loggedIn, "refresh"))
                .exchange();
        assertThat(refreshed).hasStatusOk();
        assertThat(refreshed).bodyJson().extractingPath("$.academy").isNull();
        assertThat(mvc.get().uri("/api/v1/me").cookie(cookie(refreshed, "access")))
                .as("회전한 access도 운영자")
                .hasStatusOk();
    }

    @Test
    void 운영자는_교사_관리자_학생_API를_못_쓴다() {
        Cookie access = cookies.operatorAccess(operatorId);
        for (String uri : new String[] {"/api/v1/teacher/classes", "/api/v1/admin/teachers", "/api/v1/student/home"}) {
            assertThat(mvc.get().uri(uri).cookie(access)).as(uri).hasStatus(403);
        }
    }

    @Test
    void 학원_값이_든_운영자_토큰도_학원_API를_못_쓴다() {
        long academyId = data.academy("아무 학원");
        Cookie access = cookies.access(operatorId, UserRole.SUPERADMIN, academyId);

        assertThat(mvc.get().uri("/api/v1/admin/teachers").cookie(access)).hasStatus(403);
        assertThat(mvc.get().uri("/api/v1/teacher/classes").cookie(access)).hasStatus(403);
    }

    @Test
    void 비활성_운영자는_로그인_401() {
        jdbc.update("update `user` set status = 'inactive' where id = ?", operatorId);

        assertThat(login(loginId, PASSWORD)).hasStatus(401);
    }

    @Test
    void 학원_칸이_빈_교사_행은_로그인_401() {
        long broken = data.staff(data.academy("임시"), "교사", "teacher");
        jdbc.update("update `user` set academy_id = null where id = ?", broken);
        identities.save(AuthIdentity.password(broken, "broken" + broken, encoder.encode(PASSWORD), clock.instant()));

        assertThat(login("broken" + broken, PASSWORD)).as("학원 없는 계정은 운영자만").hasStatus(401);
    }
}
