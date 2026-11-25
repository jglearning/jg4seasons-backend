package com.jglearning.jg4seasons.talk.web;

import static com.jglearning.jg4seasons.talk.web.TalkTestSupport.assertProblem;
import static com.jglearning.jg4seasons.talk.web.TalkTestSupport.create;
import static com.jglearning.jg4seasons.talk.web.TalkTestSupport.createRaw;
import static com.jglearning.jg4seasons.talk.web.TalkTestSupport.features;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.MutableClock;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.auth.AuthCookieSupport;
import com.jglearning.jg4seasons.talk.realtime.FakeRealtimeSessionIssuer;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

/** POST /talk/sessions(계약 D-2 + 추가 지시 가-2 closingInstruction). 발급기는 가짜 */
@IntegrationTest
@Transactional
class TalkSessionCreateTest {

    static final String TODAY_LESSON = "L2-B1-03";
    static final Instant NOW = Instant.parse("2026-11-26T03:00:00Z"); // 서울 11/26 12:00

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
    long classId;
    long studentId;

    @BeforeEach
    void setUp() {
        clock.set(NOW);
        issuer.reset();
        data = new TestData(jdbc);
        academyId = data.academy("대화 학원");
        features(jdbc, academyId, true, true);
        classId = data.clazz(academyId, "L2-B1", "그린 반", TestData.uniqueAccessCode(), "active");
        studentId = data.student(academyId, "대화 학생");
        data.enroll(classId, studentId);
        data.session(classId, 1, TODAY_LESSON, LocalDate.of(2026, 11, 26), "scheduled");
        data.word(TODAY_LESSON, 1, "cheerful", "book", "adj", false);
        data.word(TODAY_LESSON, 2, "make friends", "extra", "phr", false);
    }

    @AfterEach
    void tearDown() {
        clock.reset();
        issuer.reset();
    }

    @Test
    void 새_세션은_201과_임시_키_마무리_신호() {
        String clientId = UUID.randomUUID().toString();

        MvcTestResult result = create(mvc, student(), clientId, "lesson", "female");

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("created");
        assertThat(result).bodyJson().extractingPath("$.maxSeconds").isEqualTo(180);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.realtime.clientSecret")
                .asString()
                .startsWith("ek_fake_");
        assertThat(result).bodyJson().extractingPath("$.realtime.expiresAt").isEqualTo("2026-11-26T03:01:00Z");
        assertThat(result).bodyJson().extractingPath("$.realtime.model").isEqualTo("gpt-realtime-2.1-mini");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.realtime.closingInstruction")
                .asString()
                .contains("time to wrap up");

        Map<String, Object> row = row(clientId);
        assertThat(row.get("id")).isEqualTo(((Number) JsonPath.read(body(result), "$.id")).longValue());
        assertThat(row.get("student_id")).isEqualTo(studentId);
        assertThat(row.get("lesson_id")).isEqualTo(data.lessonId(TODAY_LESSON));
        assertThat(row.get("level_code")).isEqualTo("L2");
        assertThat(row.get("topic")).isEqualTo("lesson");
        assertThat(row.get("voice")).isEqualTo("female");
        assertThat(row.get("status")).isEqualTo("created");
        assertThat(row.get("model")).isEqualTo("gpt-realtime-2.1-mini");
        assertThat(row.get("prompt_version")).isEqualTo("talk/v1");
        assertThat(row.get("started_at")).isNull();
    }

    @Test
    void 지시문에_오늘_회차_단어가_들어가고_음성은_설정_이름으로() {
        create(mvc, student(), UUID.randomUUID().toString(), "lesson", "female");
        assertThat(issuer.lastRequest().instructions()).contains("Lesson words: cheerful, make friends");
        assertThat(issuer.lastRequest().instructions()).contains("8 to 10 words"); // 그린 레벨 기준
        assertThat(issuer.lastRequest().voice()).isEqualTo("marin");

        create(mvc, student(), UUID.randomUUID().toString(), "day", "male");
        assertThat(issuer.lastRequest().voice()).isEqualTo("cedar");
    }

    @Test
    void 같은_clientSessionId를_다시_보내면_200_같은_세션_새_키() {
        String clientId = UUID.randomUUID().toString();
        String first =
                JsonPath.read(body(create(mvc, student(), clientId, "day", "female")), "$.realtime.clientSecret");

        MvcTestResult again = create(mvc, student(), clientId, "day", "female");

        assertThat(again).hasStatusOk();
        assertThat(again).bodyJson().extractingPath("$.status").isEqualTo("created");
        assertThat((String) JsonPath.read(body(again), "$.realtime.clientSecret"))
                .startsWith("ek_fake_")
                .isNotEqualTo(first);
        assertThat(count()).isEqualTo(1);
    }

    @Test
    void 끝난_세션의_clientSessionId를_다시_보내면_200에_realtime_null() {
        String clientId = UUID.randomUUID().toString();
        create(mvc, student(), clientId, "day", "female");
        jdbc.update("update talk_session set status = 'ended' where client_session_id = ?", clientId);

        MvcTestResult again = create(mvc, student(), clientId, "day", "female");

        assertThat(again).hasStatusOk();
        assertThat(again).bodyJson().extractingPath("$.status").isEqualTo("ended");
        assertThat(again).bodyJson().extractingPath("$.realtime").isNull();
    }

    @Test
    void 다른_학생이_쓴_clientSessionId는_400() {
        String clientId = UUID.randomUUID().toString();
        create(mvc, student(), clientId, "day", "female");
        long other = data.student(academyId, "다른 학생");
        data.enroll(classId, other);

        assertProblem(
                create(mvc, cookies.studentAccess(other, academyId), clientId, "day", "female"),
                HttpStatus.BAD_REQUEST,
                "VALIDATION");
    }

    @Test
    void 하루_4번째는_429_DAILY_LIMIT이고_재전송은_한도와_무관() {
        String last = null;
        for (int i = 0; i < 3; i++) {
            last = UUID.randomUUID().toString();
            assertThat(create(mvc, student(), last, "day", "female")).hasStatus(HttpStatus.CREATED);
        }

        assertProblem(
                create(mvc, student(), UUID.randomUUID().toString(), "day", "female"),
                HttpStatus.TOO_MANY_REQUESTS,
                "DAILY_LIMIT");
        assertThat(create(mvc, student(), last, "day", "female")).hasStatusOk();
    }

    @Test
    void 하루는_학원_타임존_자정에_바뀐다() {
        clock.set(Instant.parse("2026-11-26T14:59:00Z")); // 서울 11/26 23:59
        for (int i = 0; i < 3; i++) {
            create(mvc, student(), UUID.randomUUID().toString(), "day", "female");
        }

        clock.set(Instant.parse("2026-11-26T15:00:00Z")); // 서울 11/27 00:00

        assertThat(create(mvc, student(), UUID.randomUUID().toString(), "day", "female"))
                .hasStatus(HttpStatus.CREATED);
    }

    @Test
    void 발급_실패는_503이고_failed로_남고_한도를_깎지_않는다() {
        String clientId = UUID.randomUUID().toString();
        issuer.failNext();

        assertProblem(
                create(mvc, student(), clientId, "day", "female"),
                HttpStatus.SERVICE_UNAVAILABLE,
                "REALTIME_UNAVAILABLE");
        assertThat(row(clientId).get("status")).isEqualTo("failed");

        for (int i = 0; i < 3; i++) {
            assertThat(create(mvc, student(), UUID.randomUUID().toString(), "day", "female"))
                    .hasStatus(HttpStatus.CREATED);
        }
    }

    @Test
    void 기능이_꺼진_학원은_404_FEATURE_DISABLED() {
        features(jdbc, academyId, false, false);

        assertProblem(
                create(mvc, student(), UUID.randomUUID().toString(), "day", "female"),
                HttpStatus.NOT_FOUND,
                "FEATURE_DISABLED");
        assertThat(count()).isZero();
    }

    @Test
    void 구독이_없으면_403_SUBSCRIPTION_REQUIRED() {
        features(jdbc, academyId, true, false);

        assertProblem(
                create(mvc, student(), UUID.randomUUID().toString(), "day", "female"),
                HttpStatus.FORBIDDEN,
                "SUBSCRIPTION_REQUIRED");
        assertThat(count()).isZero();
    }

    @Test
    void 사용_기간이_아니면_403_LICENSE_INACTIVE() {
        data.enforceLicenses(academyId);
        data.license(academyId, "L2-B1", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 11, 1), 30);

        assertProblem(
                create(mvc, student(), UUID.randomUUID().toString(), "day", "female"),
                HttpStatus.FORBIDDEN,
                "LICENSE_INACTIVE");
        assertThat(count()).isZero();
    }

    @Test
    void 개강_전이면_회차_없이_만들고_lesson_주제는_단어_없이() {
        clock.set(Instant.parse("2026-11-20T03:00:00Z"));
        String clientId = UUID.randomUUID().toString();

        assertThat(create(mvc, student(), clientId, "lesson", "female")).hasStatus(HttpStatus.CREATED);
        assertThat(row(clientId).get("lesson_id")).isNull();
        assertThat(issuer.lastRequest().instructions()).doesNotContain("Lesson words");
    }

    @Test
    void 정해진_값이_아니면_400_VALIDATION() {
        assertProblem(
                create(mvc, student(), UUID.randomUUID().toString(), "weather", "female"),
                HttpStatus.BAD_REQUEST,
                "VALIDATION");
        assertProblem(
                create(mvc, student(), UUID.randomUUID().toString(), "day", "FEMALE"),
                HttpStatus.BAD_REQUEST,
                "VALIDATION");
        assertProblem(create(mvc, student(), "not-a-uuid", "day", "female"), HttpStatus.BAD_REQUEST, "VALIDATION");
        assertProblem(
                createRaw(mvc, student(), "{\"clientSessionId\":\"" + UUID.randomUUID() + "\",\"topic\":\"day\"}"),
                HttpStatus.BAD_REQUEST,
                "VALIDATION");
        assertThat(count()).isZero();
    }

    private Cookie student() {
        return cookies.studentAccess(studentId, academyId);
    }

    private Map<String, Object> row(String clientId) {
        return jdbc.queryForMap("select * from talk_session where client_session_id = ?", clientId);
    }

    private int count() {
        Integer n =
                jdbc.queryForObject("select count(*) from talk_session where student_id = ?", Integer.class, studentId);
        return n == null ? 0 : n;
    }

    private static String body(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
