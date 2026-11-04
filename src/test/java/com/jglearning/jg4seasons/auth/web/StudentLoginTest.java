package com.jglearning.jg4seasons.auth.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.MutableClock;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.auth.AuthIdentity;
import com.jglearning.jg4seasons.auth.AuthIdentityRepository;
import com.jglearning.jg4seasons.auth.PersonalCodeHasher;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

/** 1.2 POST /auth/student/login — 가입 코드 + 개인 코드 → 쿠키 3개. 찍기 방지(AUTH-07). */
@IntegrationTest
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class StudentLoginTest {

    static final String PERSONAL_CODE = "3817";
    static final String WRONG_CODE = "9999";
    /** 잠금·요청 한도 카운터는 컨텍스트에 남는다 → 테스트마다 다른 IP */
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

    @Autowired
    EntityManager entityManager;

    TestData data;
    long academyId;
    String accessCode;
    long classId;
    long studentId;
    String ip;

    @BeforeEach
    void setUp() {
        clock.set(Clock.systemUTC().instant().truncatedTo(ChronoUnit.SECONDS));
        data = new TestData(jdbc);
        academyId = data.academy("테스트 학원");
        accessCode = TestData.uniqueAccessCode();
        classId = data.clazz(academyId, "L2-B1", "겨울 그린 A반", accessCode, "active");
        studentId = enrolledStudent("김학생", PERSONAL_CODE, classId);
        ip = "203.0.113." + IP_SEQ.getAndIncrement();
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    private long enrolledStudent(String name, String personalCode, long inClass) {
        long id = data.student(academyId, name);
        data.enroll(inClass, id);
        identities.save(AuthIdentity.personalCode(id, hasher.hash(academyId, personalCode), Instant.now(clock)));
        return id;
    }

    private MvcTestResult login(String code, String personalCode) {
        return loginBody("{\"code\":\"" + code + "\",\"personalCode\":\"" + personalCode + "\"}");
    }

    private MvcTestResult loginBody(String body) {
        return mvc.post()
                .uri("/api/v1/auth/student/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
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
    void 맞는_코드면_200과_쿠키_3개() throws Exception {
        MvcTestResult result = login(accessCode, PERSONAL_CODE);

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.user.id").asNumber().isEqualTo((int) studentId);
        assertThat(result).bodyJson().extractingPath("$.user.name").isEqualTo("김학생");
        assertThat(result).bodyJson().extractingPath("$.user.role").isEqualTo("student");
        assertThat(result).bodyJson().extractingPath("$.class.id").asNumber().isEqualTo((int) classId);
        assertThat(result).bodyJson().extractingPath("$.class.name").isEqualTo("겨울 그린 A반");
        assertThat(setCookies(result))
                .hasSize(3)
                .anyMatch(c -> c.startsWith("access=") && c.contains("Path=/api;") && c.contains("HttpOnly"))
                .anyMatch(c -> c.startsWith("refresh=") && c.contains("Path=/api/v1/auth") && c.contains("HttpOnly"))
                .anyMatch(c -> c.startsWith("csrf=") && c.contains("Path=/") && !c.contains("HttpOnly"));

        String body = result.getResponse().getContentAsString();
        for (Cookie cookie : result.getResponse().getCookies()) {
            assertThat(body).as("응답 바디에 토큰 없음(%s)", cookie.getName()).doesNotContain(cookie.getValue());
        }
    }

    @Test
    void 받은_쿠키로_GET_me가_된다() {
        Cookie[] cookies = login(accessCode, PERSONAL_CODE).getResponse().getCookies();

        MvcTestResult me = mvc.get().uri("/api/v1/me").cookie(cookies).exchange();

        assertThat(me).hasStatusOk();
        assertThat(me).bodyJson().extractingPath("$.id").asNumber().isEqualTo((int) studentId);
    }

    @Test
    void 로그인하면_마지막_로그인_시각을_남긴다() {
        login(accessCode, PERSONAL_CODE);
        entityManager.flush(); // 같은 트랜잭션 안에서 JDBC로 읽기 전에 JPA 변경을 내보낸다

        assertThat(jdbc.queryForObject(
                        "select last_login_at is not null from `user` where id = ?", Boolean.class, studentId))
                .isTrue();
    }

    @Test
    void 소문자와_공백_가입_코드도_된다() {
        assertThat(login(" " + accessCode.toLowerCase() + " ", PERSONAL_CODE)).hasStatusOk();
    }

    @Test
    void 영으로_시작하는_개인_코드도_된다() {
        enrolledStudent("이학생", "0423", classId);

        assertThat(login(accessCode, "0423")).hasStatusOk();
    }

    @Test
    void CSRF_헤더_없이_로그인된다() {
        assertThat(login(accessCode, PERSONAL_CODE)).hasStatusOk();
    }

    @Test
    void 틀린_개인_코드는_401_INVALID_CREDENTIALS() {
        MvcTestResult result = login(accessCode, WRONG_CODE);

        assertThat(result).hasStatus(401).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_CREDENTIALS");
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("가입 코드 또는 개인 코드가 맞지 않아요");
        assertThat(setCookies(result)).isEmpty();
    }

    @Test
    void 없는_가입_코드도_같은_401과_문구() throws Exception {
        String wrongPersonal = login(accessCode, WRONG_CODE).getResponse().getContentAsString();
        String wrongClass =
                login(TestData.uniqueAccessCode(), PERSONAL_CODE).getResponse().getContentAsString();

        assertThat(wrongClass).contains("\"INVALID_CREDENTIALS\"");
        assertThat(field(wrongClass, "title")).isEqualTo(field(wrongPersonal, "title"));
        assertThat(field(wrongClass, "detail")).isEqualTo(field(wrongPersonal, "detail"));
    }

    @Test
    void 다른_반_학생의_개인_코드는_401이다() {
        long otherClass = data.clazz(academyId, "L2-B1", "B반", TestData.uniqueAccessCode(), "active");
        enrolledStudent("다른반학생", "5555", otherClass);

        assertThat(login(accessCode, "5555"))
                .as("같은 학원이라 HMAC으로 학생은 찾아지지만 이 반 소속이 아님")
                .hasStatus(401);
    }

    @Test
    void 퇴원한_학생은_401() {
        data.leave(classId, studentId);

        assertThat(login(accessCode, PERSONAL_CODE)).hasStatus(401);
    }

    @Test
    void 비활성_학생은_401() {
        jdbc.update("update `user` set status = 'inactive' where id = ?", studentId);

        assertThat(login(accessCode, PERSONAL_CODE)).hasStatus(401);
    }

    @Test
    void 다른_학원_학생의_코드는_401() {
        long otherAcademy = data.academy("다른 학원");
        String otherCode = TestData.uniqueAccessCode();
        data.clazz(otherAcademy, "L2-B1", "다른 학원 반", otherCode, "active");

        assertThat(login(otherCode, PERSONAL_CODE))
                .as("HMAC에 학원 id가 들어가 다른 학원에서는 찾아지지 않음")
                .hasStatus(401);
    }

    @Test
    void 정지된_학원은_401() {
        jdbc.update("update academy set status = 'suspended' where id = ?", academyId);

        assertThat(login(accessCode, PERSONAL_CODE)).hasStatus(401);
    }

    @Test
    void 같은_반_같은_IP_5번째_실패부터_423_LOCKED와_until() {
        for (int i = 0; i < 4; i++) {
            assertThat(login(accessCode, WRONG_CODE)).as("%d번째", i + 1).hasStatus(401);
        }
        MvcTestResult locked = login(accessCode, WRONG_CODE);

        assertThat(locked).hasStatus(423).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(locked).bodyJson().extractingPath("$.code").isEqualTo("LOCKED");
        assertThat(locked)
                .bodyJson()
                .extractingPath("$.until")
                .isEqualTo(clock.instant().plus(Duration.ofMinutes(10)).toString());
    }

    @Test
    void 잠긴_동안은_맞는_코드도_423이다() {
        lockOut();

        assertThat(login(accessCode, PERSONAL_CODE)).hasStatus(423);
    }

    @Test
    void 십분_뒤에는_맞는_코드로_들어간다() {
        lockOut();

        clock.advance(Duration.ofMinutes(10));

        assertThat(login(accessCode, PERSONAL_CODE)).hasStatusOk();
    }

    @Test
    void 다른_IP는_잠기지_않는다() {
        lockOut();
        ip = "203.0.113.250";

        assertThat(login(accessCode, PERSONAL_CODE)).hasStatusOk();
    }

    @Test
    void 없는_가입_코드_반복은_11번째에_429() {
        for (int i = 0; i < 10; i++) {
            assertThat(login(TestData.uniqueAccessCode(), PERSONAL_CODE)).hasStatus(401);
        }
        MvcTestResult limited = login(TestData.uniqueAccessCode(), PERSONAL_CODE);

        assertThat(limited).hasStatus(429);
        assertThat(limited).bodyJson().extractingPath("$.code").isEqualTo("RATE_LIMITED");
    }

    @Test
    void 개인_코드가_숫자_JSON이면_400() {
        MvcTestResult result = loginBody("{\"code\":\"" + accessCode + "\",\"personalCode\":3817}");

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION");
    }

    @Test
    void 개인_코드_3자리는_400이고_실패로_세지_않는다() {
        for (int i = 0; i < 5; i++) {
            assertThat(login(accessCode, "381")).hasStatus(400);
        }

        assertThat(login(accessCode, PERSONAL_CODE)).as("형식 오류는 잠금에 들어가지 않음").hasStatusOk();
    }

    @Test
    void 로그에_개인_코드와_가입_코드가_남지_않는다(CapturedOutput output) {
        login(accessCode, WRONG_CODE);
        login(accessCode, PERSONAL_CODE);
        lockOut();

        // 앱 로그 줄만 본다(레벨 + [요청 ID]). MockMvc가 테스트 출력에 찍는 요청 덤프에는 본문이 들어 있다
        List<String> appLogs = output.getOut()
                .lines()
                .filter(line -> line.matches(".*\\b(TRACE|DEBUG|INFO|WARN|ERROR) \\[.*"))
                .toList();
        assertThat(appLogs).isNotEmpty();
        assertThat(appLogs)
                .noneMatch(
                        line -> line.contains(PERSONAL_CODE) || line.contains(WRONG_CODE) || line.contains(accessCode));
    }

    private void lockOut() {
        for (int i = 0; i < 5; i++) {
            login(accessCode, WRONG_CODE);
        }
        assertThat(login(accessCode, WRONG_CODE)).hasStatus(423);
    }

    private static String field(String json, String name) {
        int start = json.indexOf("\"" + name + "\"");
        return start < 0
                ? null
                : json.substring(start, json.indexOf(',', start) < 0 ? json.length() : json.indexOf(',', start));
    }
}
