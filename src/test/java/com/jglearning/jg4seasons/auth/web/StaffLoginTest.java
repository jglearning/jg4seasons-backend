package com.jglearning.jg4seasons.auth.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.MutableClock;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.auth.AuthIdentity;
import com.jglearning.jg4seasons.auth.AuthIdentityRepository;
import com.jglearning.jg4seasons.auth.PersonalCodeHasher;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

/** 1.4 POST /auth/staff/login — 아이디 + 비밀번호 → 쿠키 3개(refresh 14일). 계정 단위 5회 실패 → 10분 잠금. */
@IntegrationTest
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class StaffLoginTest {

    static final String PASSWORD = "teacher-pass-1";
    static final String WRONG = "wrong-pass-99";
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
    PersonalCodeHasher hasher;

    @Autowired
    MutableClock clock;

    @Autowired
    EntityManager entityManager;

    TestData data;
    long academyId;
    long teacherId;
    String loginId;
    String ip;

    @BeforeEach
    void setUp() {
        clock.set(Clock.systemUTC().instant().truncatedTo(ChronoUnit.SECONDS));
        data = new TestData(jdbc);
        academyId = data.academy("테스트 학원");
        teacherId = data.staff(academyId, "김교사", "teacher");
        loginId = "teacher" + teacherId;
        identities.save(AuthIdentity.password(teacherId, loginId, encoder.encode(PASSWORD), clock.instant()));
        ip = "198.18.0." + IP_SEQ.getAndIncrement();
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

    static List<String> setCookies(MvcTestResult result) {
        return result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
    }

    @Test
    void 맞으면_200과_쿠키_3개_refresh는_14일() {
        MvcTestResult result = login(loginId, PASSWORD);

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.user.id").asNumber().isEqualTo((int) teacherId);
        assertThat(result).bodyJson().extractingPath("$.user.name").isEqualTo("김교사");
        assertThat(result).bodyJson().extractingPath("$.user.role").isEqualTo("teacher");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.user.academyId")
                .asNumber()
                .isEqualTo((int) academyId);
        assertThat(setCookies(result))
                .hasSize(3)
                .anyMatch(c -> c.startsWith("refresh=") && c.contains("Max-Age=1209600"))
                .anyMatch(c -> c.startsWith("access=") && c.contains("Max-Age=1800"));
    }

    @Test
    void 관리자도_로그인한다() {
        long adminId = data.staff(academyId, "관리자", "admin");
        identities.save(AuthIdentity.password(adminId, "admin" + adminId, encoder.encode(PASSWORD), clock.instant()));

        assertThat(login("admin" + adminId, PASSWORD))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.user.role")
                .isEqualTo("admin");
    }

    @Test
    void 아이디는_대소문자와_앞뒤_공백을_정리한다() {
        assertThat(login("  " + loginId.toUpperCase() + " ", PASSWORD)).hasStatusOk();
    }

    @Test
    void 없는_아이디와_틀린_비밀번호는_같은_401() throws Exception {
        MvcTestResult wrongPassword = login(loginId, WRONG);
        MvcTestResult unknown = login("nobody-here", PASSWORD);

        assertThat(wrongPassword).hasStatus(401);
        assertThat(unknown).hasStatus(401);
        for (String field : List.of("$.code", "$.title", "$.detail")) {
            Object expected = JsonPath.read(wrongPassword.getResponse().getContentAsString(), field);
            assertThat(expected).as(field).isNotNull();
            assertThat(unknown).bodyJson().extractingPath(field).isEqualTo(expected);
        }
        assertThat(wrongPassword).bodyJson().extractingPath("$.code").isEqualTo("INVALID_CREDENTIALS");
        assertThat(wrongPassword).bodyJson().extractingPath("$.title").isEqualTo("입력한 정보가 맞지 않아요");
        assertThat(wrongPassword).bodyJson().extractingPath("$.detail").isEqualTo("아이디 또는 비밀번호가 맞지 않아요");
    }

    @Test
    void 다섯_번째_실패부터_423과_until() {
        for (int i = 0; i < 4; i++) {
            assertThat(login(loginId, WRONG)).as("%d번째", i + 1).hasStatus(401);
        }
        MvcTestResult locked = login(loginId, WRONG);

        assertThat(locked).hasStatus(423);
        assertThat(locked).bodyJson().extractingPath("$.code").isEqualTo("LOCKED");
        assertThat(locked)
                .bodyJson()
                .extractingPath("$.until")
                .isEqualTo(clock.instant().plus(Duration.ofMinutes(10)).toString());
    }

    @Test
    void 잠긴_동안은_맞는_비밀번호도_423이고_10분_뒤에는_들어간다() {
        for (int i = 0; i < 5; i++) {
            login(loginId, WRONG);
        }
        assertThat(login(loginId, PASSWORD)).hasStatus(423);

        clock.advance(Duration.ofMinutes(10));

        assertThat(login(loginId, PASSWORD)).hasStatusOk();
    }

    @Test
    void IP를_바꿔도_계정_잠금은_피할_수_없다() {
        for (int i = 0; i < 5; i++) {
            ip = "198.18.1." + i;
            login(loginId, WRONG);
        }
        ip = "198.18.1.99";

        assertThat(login(loginId, PASSWORD)).hasStatus(423);
    }

    @Test
    void 성공하면_실패_횟수가_0이다() {
        for (int i = 0; i < 4; i++) {
            login(loginId, WRONG);
        }
        assertThat(login(loginId, PASSWORD)).hasStatusOk();

        for (int i = 0; i < 4; i++) {
            assertThat(login(loginId, WRONG)).hasStatus(401);
        }
    }

    @Test
    void 비활성_교사는_401() {
        jdbc.update("update `user` set status = 'inactive' where id = ?", teacherId);

        assertThat(login(loginId, PASSWORD)).hasStatus(401);
    }

    @Test
    void 정지된_학원은_401() {
        jdbc.update("update academy set status = 'suspended' where id = ?", academyId);

        assertThat(login(loginId, PASSWORD)).hasStatus(401);
    }

    @Test
    void 학생은_비밀번호_수단이_있어도_staff_로그인을_못_한다() {
        long studentId = data.student(academyId, "학생");
        identities.save(
                AuthIdentity.password(studentId, "student" + studentId, encoder.encode(PASSWORD), clock.instant()));

        assertThat(login("student" + studentId, PASSWORD)).hasStatus(401);
    }

    @Test
    void 없는_아이디_11번째는_429() {
        for (int i = 0; i < 10; i++) {
            assertThat(login("nobody-" + i, PASSWORD)).hasStatus(401);
        }
        MvcTestResult limited = login("nobody-x", PASSWORD);

        assertThat(limited).hasStatus(429);
        assertThat(limited).bodyJson().extractingPath("$.code").isEqualTo("RATE_LIMITED");
    }

    @Test
    void 있는_계정들에_비밀번호를_대입해도_IP당_11번째는_429() {
        // password spraying: 계정마다 5회 미만으로 나눠 찍으면 계정 잠금에 걸리지 않는다 → IP 한도가 모든 시도를 센다
        long[] others = new long[3];
        for (int i = 0; i < 3; i++) {
            others[i] = data.staff(academyId, "교사" + i, "teacher");
            identities.save(
                    AuthIdentity.password(others[i], "spray" + others[i], encoder.encode(PASSWORD), clock.instant()));
        }
        for (int i = 0; i < 10; i++) {
            assertThat(login("spray" + others[i % 3], WRONG)).as("%d번째", i + 1).hasStatus(401);
        }

        assertThat(login("spray" + others[0], WRONG)).hasStatus(429);
    }

    @Test
    void 한도를_넘으면_있는_아이디도_없는_아이디도_같은_429() {
        for (int i = 0; i < 10; i++) {
            login("nobody-" + i, PASSWORD);
        }

        assertThat(login(loginId, PASSWORD)).as("있는 계정·맞는 비밀번호도").hasStatus(429);
        assertThat(login("nobody-x", PASSWORD)).hasStatus(429);
    }

    @Test
    void 형식이_틀리면_400() {
        assertThat(login(loginId, "1234567")).as("비밀번호 7자").hasStatus(400);
        assertThat(login("ab", PASSWORD)).as("아이디 2자").hasStatus(400);
        assertThat(login("bad id!", PASSWORD)).as("허용 안 되는 문자").hasStatus(400);
    }

    @Test
    void CSRF_헤더_없이_로그인된다() {
        assertThat(login(loginId, PASSWORD)).hasStatusOk();
    }

    @Test
    void 거부_사유가_사용자_id와_함께_로그에_남는다(CapturedOutput output) {
        login(loginId, WRONG);
        jdbc.update("update `user` set status = 'inactive' where id = ?", teacherId);
        entityManager.clear();
        login(loginId, PASSWORD);

        assertThat(output.getOut())
                .contains("staff login wrong password userId=" + teacherId + " attempts=1")
                .contains("staff login rejected: not eligible userId=" + teacherId);
    }

    @Test
    void 로그인하면_마지막_로그인_시각과_사용_시각이_남는다() {
        login(loginId, PASSWORD);
        entityManager.flush();

        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        assertThat(jdbc.queryForObject("select last_login_at from `user` where id = ?", LocalDateTime.class, teacherId))
                .isEqualTo(now);
        assertThat(jdbc.queryForObject(
                        "select last_used_at from auth_identity where user_id = ?", LocalDateTime.class, teacherId))
                .isEqualTo(now);
    }

    @Test
    void 로그에_비밀번호가_남지_않는다(CapturedOutput output) {
        login(loginId, WRONG);
        login(loginId, PASSWORD);
        for (int i = 0; i < 5; i++) {
            login(loginId, WRONG);
        }

        List<String> appLogs = output.getOut()
                .lines()
                .filter(line -> line.matches(".*\\b(TRACE|DEBUG|INFO|WARN|ERROR) \\[.*"))
                .toList();
        assertThat(appLogs).isNotEmpty();
        assertThat(appLogs).noneMatch(line -> line.contains(PASSWORD) || line.contains(WRONG));
    }
}
