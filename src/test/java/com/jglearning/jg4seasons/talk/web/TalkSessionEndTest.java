package com.jglearning.jg4seasons.talk.web;

import static com.jglearning.jg4seasons.talk.web.TalkTestSupport.assertProblem;
import static com.jglearning.jg4seasons.talk.web.TalkTestSupport.create;
import static com.jglearning.jg4seasons.talk.web.TalkTestSupport.features;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.MutableClock;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.auth.AuthCookieSupport;
import com.jglearning.jg4seasons.talk.summary.FakeTalkSummarizer;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

/** POST /talk/sessions/{id}/end(계약 D-3)·GET /talk/sessions/{id}(D-4). 요약기는 가짜 */
@IntegrationTest
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class TalkSessionEndTest {

    static final String TURNS = "[{\"role\":\"character\",\"text\":\"Hi! What did you do today?\"},"
            + "{\"role\":\"student\",\"text\":\"I go to school and play soccer.\"},"
            + "{\"role\":\"character\",\"text\":\"Nice!\"},"
            + "{\"role\":\"student\",\"text\":\"My friend is play with me.\"}]";

    @Autowired
    MockMvcTester mvc;

    @Autowired
    AuthCookieSupport cookies;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MutableClock clock;

    @Autowired
    FakeTalkSummarizer summarizer;

    @PersistenceContext
    EntityManager entities;

    TestData data;
    long academyId;
    long classId;
    long studentId;

    @BeforeEach
    void setUp() {
        clock.set(Instant.parse("2026-11-26T03:00:00Z"));
        summarizer.reset();
        data = new TestData(jdbc);
        academyId = data.academy("종료 학원");
        features(jdbc, academyId, true, true);
        classId = data.clazz(academyId, "L2-B1", "그린 반", TestData.uniqueAccessCode(), "active");
        studentId = data.student(academyId, "대화 학생");
        data.enroll(classId, studentId);
        data.session(classId, 1, "L2-B1-03", LocalDate.of(2026, 11, 26), "scheduled");
    }

    @AfterEach
    void tearDown() {
        clock.reset();
        summarizer.reset();
    }

    @Test
    void 종료하면_글자_기록이_저장되고_요약이_온다() {
        long id = newSession();

        MvcTestResult result = end(student(), id, body(168, "user", TURNS, "{\"total_tokens\":253}"));

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.id").isEqualTo((int) id);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("ended");
        assertThat(result).bodyJson().extractingPath("$.durationSec").isEqualTo(168);
        assertThat(result).bodyJson().extractingPath("$.studentTurnCount").isEqualTo(2);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.summary.goodExpressions")
                .isEqualTo(List.of("play soccer"));
        assertThat(result)
                .bodyJson()
                .extractingPath("$.summary.corrections[0].said")
                .isEqualTo("I go to school and play soccer.");
        assertThat(turns(id))
                .containsExactly(
                        "1:character:Hi! What did you do today?",
                        "2:student:I go to school and play soccer.",
                        "3:character:Nice!",
                        "4:student:My friend is play with me.");
        Map<String, Object> row = jdbc.queryForMap("select end_reason, usage_json from talk_session where id = ?", id);
        assertThat(row.get("end_reason")).isEqualTo("user");
        assertThat(String.valueOf(row.get("usage_json"))).contains("253");
    }

    @Test
    void 요약이_실패해도_ended이고_summary는_null() {
        long id = newSession();
        summarizer.failNext();

        MvcTestResult result = end(student(), id, body(60, "timeout", TURNS, null));

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("ended");
        assertThat(result).bodyJson().extractingPath("$.summary").isNull();
        assertThat(turns(id)).hasSize(4);
    }

    @Test
    void 요약_단계에서_예외가_나도_200에_ended이고_summary는_null() {
        // 리뷰 2: 종료는 이미 커밋됨 — 요약·요약 저장 실패가 500으로 새지 않게
        long id = newSession();
        summarizer.throwNext();

        MvcTestResult result = end(student(), id, body(60, "user", TURNS, null));

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("ended");
        assertThat(result).bodyJson().extractingPath("$.summary").isNull();
        assertThat(turns(id)).hasSize(4);
    }

    @Test
    void 두_번_종료해도_같은_결과이고_다시_저장하거나_요약하지_않는다() {
        long id = newSession();
        String first = text(end(student(), id, body(168, "user", TURNS, null)));

        String second = text(end(student(), id, body(10, "left", "[]", null)));

        assertThat(second).isEqualTo(first);
        assertThat(summarizer.calls()).isEqualTo(1);
        assertThat(turns(id)).hasSize(4);
    }

    @Test
    void 긴_turns와_durationSec는_잘려서_저장된다() {
        long id = newSession();
        String many = IntStream.rangeClosed(1, 250)
                .mapToObj(i -> "{\"role\":\"student\",\"text\":\"" + (i == 1 ? "x".repeat(600) : "line " + i) + "\"}")
                .collect(Collectors.joining(",", "[", "]"));

        MvcTestResult result = end(student(), id, body(999, "timeout", many, null));

        assertThat(result).bodyJson().extractingPath("$.durationSec").isEqualTo(180);
        assertThat(result).bodyJson().extractingPath("$.studentTurnCount").isEqualTo(200);
        assertThat(turns(id)).hasSize(200);
        assertThat(jdbc.queryForObject(
                        "select char_length(text) from talk_turn where session_id = ? and seq = 1", Integer.class, id))
                .isEqualTo(500);
        assertThat(summarizer.lastTurns()).hasSize(200);
    }

    @Test
    void 남의_세션_종료와_조회는_404() {
        long id = newSession();
        long friend = data.student(academyId, "친구");
        data.enroll(classId, friend);
        Cookie other = cookies.studentAccess(friend, academyId);

        assertProblem(end(other, id, body(60, "user", TURNS, null)), HttpStatus.NOT_FOUND, "NOT_FOUND");
        assertProblem(get(other, id), HttpStatus.NOT_FOUND, "NOT_FOUND");
        assertThat(turns(id)).isEmpty();
    }

    @Test
    void 다른_학원_학생과_없는_세션은_404() {
        long id = newSession();
        long otherAcademy = data.academy("다른 학원");
        features(jdbc, otherAcademy, true, true);
        long stranger = data.student(otherAcademy, "다른 학원 학생");
        Cookie strangerCookie = cookies.studentAccess(stranger, otherAcademy);

        assertProblem(end(strangerCookie, id, body(60, "user", TURNS, null)), HttpStatus.NOT_FOUND, "NOT_FOUND");
        assertProblem(get(strangerCookie, id), HttpStatus.NOT_FOUND, "NOT_FOUND");
        assertProblem(end(student(), 999_999_999L, body(60, "user", TURNS, null)), HttpStatus.NOT_FOUND, "NOT_FOUND");
        assertProblem(get(student(), 999_999_999L), HttpStatus.NOT_FOUND, "NOT_FOUND");
    }

    @Test
    void 기능이_꺼진_학원은_404_FEATURE_DISABLED() {
        long id = newSession();
        features(jdbc, academyId, false, false);
        entities.clear();

        assertProblem(end(student(), id, body(60, "user", TURNS, null)), HttpStatus.NOT_FOUND, "FEATURE_DISABLED");
        assertProblem(get(student(), id), HttpStatus.NOT_FOUND, "FEATURE_DISABLED");
    }

    @Test
    void expired_세션_종료도_저장하지_않고_그대로() {
        // 리뷰 3(사용자 A): 종료할 수 있는 건 created뿐 — 정리 작업(#79)이 expired로 바꾼 세션을 늦은 종료가 덮지 않게
        long id = newSession();
        jdbc.update("update talk_session set status = 'expired' where id = ?", id);

        MvcTestResult result = end(student(), id, body(60, "user", TURNS, null));

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("expired");
        assertThat(turns(id)).isEmpty();
        assertThat(summarizer.calls()).isZero();
    }

    @Test
    void failed_세션_종료는_저장하지_않고_그대로() {
        long id = newSession();
        jdbc.update("update talk_session set status = 'failed' where id = ?", id);

        MvcTestResult result = end(student(), id, body(60, "user", TURNS, null));

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("failed");
        assertThat(result).bodyJson().extractingPath("$.durationSec").isNull();
        assertThat(turns(id)).isEmpty();
        assertThat(summarizer.calls()).isZero();
    }

    @Test
    void 조회는_종료_전이면_null_칸들이고_종료_뒤에는_종료_응답과_같다() {
        long id = newSession();

        MvcTestResult before = get(student(), id);
        assertThat(before).hasStatusOk();
        assertThat(before).bodyJson().extractingPath("$.status").isEqualTo("created");
        assertThat(before).bodyJson().extractingPath("$.durationSec").isNull();
        assertThat(before).bodyJson().extractingPath("$.studentTurnCount").isNull();
        assertThat(before).bodyJson().extractingPath("$.summary").isNull();

        String ended = text(end(student(), id, body(168, "user", TURNS, null)));
        assertThat(text(get(student(), id))).isEqualTo(ended);
    }

    // 리뷰 4: 저장 형식 고정 — 이 JSON 모양이 바뀌면 지난 세션 요약(학생·교사 화면)이 조용히 사라진다

    @Test
    void 저장된_요약_형식을_그대로_읽는다() {
        long id = newSession();
        jdbc.update(
                "update talk_session set status = 'ended', summary_json = ? where id = ?",
                "{\"goodExpressions\":[\"play soccer\"],\"corrections\":[{\"said\":\"I go.\",\"better\":\"I went.\","
                        + "\"note\":\"과거형으로 말해요\"}]}",
                id);

        MvcTestResult result = get(student(), id);

        assertThat(result)
                .bodyJson()
                .extractingPath("$.summary.goodExpressions")
                .isEqualTo(List.of("play soccer"));
        assertThat(result)
                .bodyJson()
                .extractingPath("$.summary.corrections[0].better")
                .isEqualTo("I went.");
    }

    @Test
    void 나중에_칸이_늘거나_빠진_요약도_읽는다() {
        long withExtra = newSession();
        jdbc.update(
                "update talk_session set summary_json = ? where id = ?",
                "{\"goodExpressions\":[\"a\"],\"corrections\":[],\"version\":2}",
                withExtra);
        long missing = newSession();
        jdbc.update("update talk_session set summary_json = ? where id = ?", "{\"goodExpressions\":[\"b\"]}", missing);

        assertThat(get(student(), withExtra))
                .bodyJson()
                .extractingPath("$.summary.goodExpressions")
                .isEqualTo(List.of("a"));
        assertThat(get(student(), missing))
                .bodyJson()
                .extractingPath("$.summary.corrections")
                .isEqualTo(List.of());
    }

    @Test
    void 타입이_다른_요약은_null이고_로그에_내용이_없다(CapturedOutput output) {
        long id = newSession();
        jdbc.update(
                "update talk_session set summary_json = ? where id = ?",
                "{\"goodExpressions\":\"I goed to school secret\",\"corrections\":[]}",
                id);

        MvcTestResult result = get(student(), id);

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.summary").isNull();
        assertThat(output.getAll()).doesNotContain("I goed to school secret");
    }

    @Test
    void 잘못된_입력은_400() {
        long id = newSession();

        assertProblem(end(student(), id, body(60, "quit", TURNS, null)), HttpStatus.BAD_REQUEST, "VALIDATION");
        assertProblem(
                end(student(), id, body(60, "user", "[{\"role\":\"teacher\",\"text\":\"hi\"}]", null)),
                HttpStatus.BAD_REQUEST,
                "VALIDATION");
        assertProblem(end(student(), id, body(-1, "user", TURNS, null)), HttpStatus.BAD_REQUEST, "VALIDATION");
        assertProblem(end(student(), id, body(60, "user", TURNS, "[1, 2]")), HttpStatus.BAD_REQUEST, "VALIDATION");
        assertProblem(
                end(student(), id, body(60, "user", TURNS, "{\"x\":\"" + "y".repeat(17_000) + "\"}")),
                HttpStatus.BAD_REQUEST,
                "VALIDATION");
        assertProblem(
                end(student(), id, "{\"durationSec\":60,\"endReason\":\"user\"}"),
                HttpStatus.BAD_REQUEST,
                "VALIDATION");
        assertThat(turns(id)).isEmpty();
    }

    private long newSession() {
        MvcTestResult created = create(mvc, student(), UUID.randomUUID().toString(), "day", "female");
        return ((Number) JsonPath.read(text(created), "$.id")).longValue();
    }

    private MvcTestResult end(Cookie access, long id, String body) {
        return mvc.post()
                .uri(TalkTestSupport.SESSIONS + "/" + id + "/end")
                .cookie(access, AuthCookieSupport.csrf())
                .header(AuthCookieSupport.CSRF_HEADER, AuthCookieSupport.CSRF_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private MvcTestResult get(Cookie access, long id) {
        return mvc.get().uri(TalkTestSupport.SESSIONS + "/" + id).cookie(access).exchange();
    }

    static String body(int durationSec, String endReason, String turns, String usage) {
        return "{\"durationSec\":" + durationSec + ",\"endReason\":\"" + endReason + "\",\"turns\":" + turns
                + (usage == null ? "" : ",\"usage\":" + usage) + "}";
    }

    private List<String> turns(long id) {
        return jdbc.queryForList(
                "select concat(seq, ':', role, ':', text) from talk_turn where session_id = ? order by seq",
                String.class,
                id);
    }

    private Cookie student() {
        return cookies.studentAccess(studentId, academyId);
    }

    private static String text(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
