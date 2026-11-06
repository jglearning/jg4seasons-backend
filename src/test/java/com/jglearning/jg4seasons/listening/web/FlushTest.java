package com.jglearning.jg4seasons.listening.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.MutableClock;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.auth.AuthCookieSupport;
import com.jglearning.jg4seasons.auth.UserRole;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
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
 * 3.1 POST /listening/flush — 청취 구간을 받아 원본을 추가하고 트랙·회차 집계를 갱신한다. 오늘 = 서울 2026-11-10. 반 L2-B1, 5회차 오늘·6회차 내일.
 * 5회차는 지문 2 × 문장 2, 속도 0.8(8125ms)·1.0(6500ms), 필수 속도 1.0, 기준 0.9.
 */
@IntegrationTest
@Transactional
class FlushTest {

    static final String URL = "/api/v1/listening/flush";
    static final Instant NOON = Instant.parse("2026-11-10T03:00:00Z");
    static final int ONE_SPEED_MS = 2 * TestData.SENTENCE_MS + TestData.GAP_MS; // 6500

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
    long classId;
    long studentId;
    Map<String, Long> tracks;
    Map<String, Long> tomorrowTracks;

    @BeforeEach
    void setUp() {
        clock.set(NOON);
        data = new TestData(jdbc);
        academyId = data.academy("테스트 학원");
        classId = data.clazz(academyId, "L2-B1", "겨울 그린 A반", TestData.uniqueAccessCode(), "active");
        studentId = data.student(academyId, "김학생");
        data.enroll(classId, studentId);
        data.session(classId, 5, "L2-B1-05", LocalDate.of(2026, 11, 10), "scheduled");
        data.session(classId, 6, "L2-B1-06", LocalDate.of(2026, 11, 11), "scheduled");
        tracks = data.lessonContent("L2-B1-05", 2, 2, List.of("0.8", "1.0"));
        tomorrowTracks = data.lessonContent("L2-B1-06", 1, 1, List.of("1.0"));
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    static String body(String sessionId, long trackId, int[]... segments) {
        return bodyWith(sessionId, trackId, null, segments);
    }

    static String bodyWith(String sessionId, long trackId, String csrf, int[]... segments) {
        String list = java.util.Arrays.stream(segments)
                .map(s -> "{\"startMs\":" + s[0] + ",\"endMs\":" + s[1] + ",\"recordedAt\":\"2026-11-10T02:59:59Z\"}")
                .collect(Collectors.joining(",", "[", "]"));
        return "{" + (csrf == null ? "" : "\"csrf\":\"" + csrf + "\",") + "\"clientSessionId\":\"" + sessionId
                + "\",\"trackId\":" + trackId + ",\"startedAt\":\"2026-11-10T02:59:00Z\",\"userAgent\":\"test\","
                + "\"segments\":" + list + ",\"final\":false}";
    }

    static int[] seg(int start, int end) {
        return new int[] {start, end};
    }

    static String newSession() {
        return UUID.randomUUID().toString();
    }

    private MvcTestResult flush(String json) {
        return flushAs(studentId, academyId, json);
    }

    private MvcTestResult flushAs(long student, long academy, String json) {
        return mvc.post()
                .uri(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .cookie(cookies.studentAccess(student, academy), AuthCookieSupport.csrf())
                .header(AuthCookieSupport.CSRF_HEADER, AuthCookieSupport.CSRF_VALUE)
                .content(json)
                .exchange();
    }

    private Map<String, Object> trackRow(long trackId) {
        return jdbc.queryForMap(
                "select merged_ranges, covered_ms, coverage, completed_at, total_play_ms, session_count"
                        + " from track_progress where student_id = ? and track_id = ?",
                studentId,
                trackId);
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    @Test
    void 첫_flush는_세션_세그먼트_집계를_만든다() {
        long one = tracks.get("p1:1.0");

        MvcTestResult result = flush(body(newSession(), one, seg(0, 3000)));

        assertThat(result).hasStatusOk();
        var json = assertThat(result).bodyJson();
        json.extractingPath("$.track.trackId").isEqualTo((int) one);
        json.extractingPath("$.track.coverage").isEqualTo(0.4615); // 3000 / 6500 내림
        json.extractingPath("$.track.completed").isEqualTo(false);
        json.extractingPath("$.lesson.code").isEqualTo("L2-B1-05");
        json.extractingPath("$.lesson.status").isEqualTo("in_progress");
        json.extractingPath("$.lesson.requiredDone").isEqualTo(0);
        json.extractingPath("$.lesson.requiredTotal").isEqualTo(2);
        json.extractingPath("$.dropped").isEqualTo(0);
        assertThat(count("select count(*) from listening_session where student_id = ?", studentId))
                .isEqualTo(1);
        assertThat(count(
                        "select count(*) from listening_segment g join listening_session s on s.id = g.listening_session_id"
                                + " where s.student_id = ?",
                        studentId))
                .isEqualTo(1);
        assertThat(trackRow(one))
                .containsEntry("merged_ranges", "[[0, 3000]]")
                .containsEntry("covered_ms", 3000)
                .containsEntry("total_play_ms", 3000)
                .containsEntry("session_count", 1);
    }

    @Test
    void 두_번째_flush는_구간을_합친다() {
        long one = tracks.get("p1:1.0");
        String session = newSession();
        flush(body(session, one, seg(0, 3000)));

        flush(body(session, one, seg(3100, 5000)));

        assertThat(trackRow(one))
                .containsEntry("merged_ranges", "[[0, 5000]]")
                .containsEntry("covered_ms", 5000)
                .containsEntry("total_play_ms", 4900)
                .containsEntry("session_count", 1);
    }

    @Test
    void 같은_구간_재전송은_집계가_같다() {
        long one = tracks.get("p1:1.0");
        String session = newSession();
        flush(body(session, one, seg(0, 3000), seg(3000, 4000)));
        Map<String, Object> once = trackRow(one);

        MvcTestResult again = flush(body(session, one, seg(3000, 4000), seg(0, 3000)));

        assertThat(again).hasStatusOk();
        assertThat(again).bodyJson().extractingPath("$.dropped").isEqualTo(0);
        assertThat(again).bodyJson().extractingPath("$.track.coverage").isEqualTo(0.6153); // 4000 / 6500 내림
        assertThat(again).bodyJson().extractingPath("$.track.completed").isEqualTo(false);
        assertThat(again).bodyJson().extractingPath("$.lesson.status").isEqualTo("in_progress");
        assertThat(again).bodyJson().extractingPath("$.lesson.requiredDone").isEqualTo(0);
        assertThat(trackRow(one))
                .containsEntry("merged_ranges", once.get("merged_ranges"))
                .containsEntry("total_play_ms", once.get("total_play_ms"))
                .containsEntry("session_count", once.get("session_count"));
        assertThat(count("select count(*) from listening_segment")).isEqualTo(2);
    }

    @Test
    void 세션이_다르면_세션_수가_늘어난다() {
        long one = tracks.get("p1:1.0");
        flush(body(newSession(), one, seg(0, 1000)));
        flush(body(newSession(), one, seg(0, 1000)));

        assertThat(trackRow(one)).containsEntry("session_count", 2).containsEntry("total_play_ms", 2000);
    }

    @Test
    void 기준을_넘으면_트랙_완료_시각을_한_번만() {
        long one = tracks.get("p1:1.0");
        String session = newSession();

        MvcTestResult first = flush(body(session, one, seg(0, 5850))); // 0.9 정확히
        clock.set(NOON.plusSeconds(60));
        flush(body(session, one, seg(5850, 6500)));

        assertThat(first).bodyJson().extractingPath("$.track.completed").isEqualTo(true);
        assertThat(first).bodyJson().extractingPath("$.track.coverage").isEqualTo(0.9);
        assertThat(trackRow(one).get("completed_at")).hasToString("2026-11-10T03:00");
    }

    @Test
    void 기준_바로_아래는_미완료() {
        MvcTestResult result = flush(body(newSession(), tracks.get("p1:1.0"), seg(0, 5849)));

        assertThat(result).bodyJson().extractingPath("$.track.completed").isEqualTo(false);
    }

    @Test
    void 필수가_아닌_속도는_지문_완료가_아니다() {
        MvcTestResult result = flush(body(newSession(), tracks.get("p1:0.8"), seg(0, 8125)));

        assertThat(result).bodyJson().extractingPath("$.track.completed").isEqualTo(true);
        assertThat(result).bodyJson().extractingPath("$.lesson.requiredDone").isEqualTo(0);
    }

    @Test
    void 모든_지문_완료면_회차_completed() {
        flush(body(newSession(), tracks.get("p1:1.0"), seg(0, 6500)));

        MvcTestResult result = flush(body(newSession(), tracks.get("p2:1.0"), seg(0, 6500)));

        var json = assertThat(result).bodyJson();
        json.extractingPath("$.lesson.status").isEqualTo("completed");
        json.extractingPath("$.lesson.requiredDone").isEqualTo(2);
        assertThat(jdbc.queryForObject(
                        "select completed_at from lesson_progress where student_id = ? and lesson_id = ?",
                        Object.class,
                        studentId,
                        data.lessonId("L2-B1-05")))
                .isNotNull();
    }

    @Test
    void 잘못된_세그먼트만_오면_200_dropped_집계_없음() {
        MvcTestResult result = flush(body(newSession(), tracks.get("p1:1.0"), seg(-5, 100), seg(100, 100)));

        assertThat(result).hasStatusOk();
        var json = assertThat(result).bodyJson();
        json.extractingPath("$.dropped").isEqualTo(2);
        json.extractingPath("$.track.coverage").isEqualTo(0);
        json.extractingPath("$.lesson.status").isEqualTo("not_started");
        assertThat(count("select count(*) from track_progress where student_id = ?", studentId))
                .isZero();
        assertThat(count("select count(*) from lesson_progress where student_id = ?", studentId))
                .isZero();
        assertThat(count("select count(*) from listening_segment")).isZero();
        assertThat(count("select count(*) from listening_session where student_id = ?", studentId))
                .as("세션은 만든다(upsert가 필터보다 먼저)")
                .isEqualTo(1);
    }

    @Test
    void 쉰한_개면_한_개_dropped() {
        int[][] segments = new int[51][];
        for (int i = 0; i < 51; i++) {
            segments[i] = seg(i * 100, i * 100 + 50);
        }

        MvcTestResult result = flush(body(newSession(), tracks.get("p1:1.0"), segments));

        assertThat(result).bodyJson().extractingPath("$.dropped").isEqualTo(1);
    }

    @Test
    void 다른_학생의_세션_id면_400_clientSessionId() {
        long other = data.student(academyId, "이학생");
        data.enroll(classId, other);
        String session = newSession();
        flushAs(other, academyId, body(session, tracks.get("p1:1.0"), seg(0, 1000)));

        MvcTestResult result = flush(body(session, tracks.get("p1:1.0"), seg(0, 1000)));

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("clientSessionId");
    }

    @Test
    void 같은_세션_id로_다른_트랙이면_400() {
        String session = newSession();
        flush(body(session, tracks.get("p1:1.0"), seg(0, 1000)));

        MvcTestResult result = flush(body(session, tracks.get("p2:1.0"), seg(0, 1000)));

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("clientSessionId");
        assertThat(count("select count(*) from track_progress where track_id = ?", tracks.get("p2:1.0")))
                .isZero();
    }

    @Test
    void 모르는_필드는_400() {
        String json = body(newSession(), tracks.get("p1:1.0"), seg(0, 1000)).replace("\"final\":false", "\"extra\":1");

        assertThat(flush(json)).hasStatus(400);
    }

    @Test
    void 세션_id_형식이_틀리면_400() {
        MvcTestResult result = flush(body("not-a-uuid", tracks.get("p1:1.0"), seg(0, 1000)));

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("clientSessionId");
    }

    @Test
    void 다른_학원_학생은_404_행이_안_생긴다() {
        long otherAcademy = data.academy("다른 학원");
        long otherClass = data.clazz(otherAcademy, "L2-B1", "다른 반", TestData.uniqueAccessCode(), "active");
        long otherStudent = data.student(otherAcademy, "박학생");
        data.enroll(otherClass, otherStudent);

        MvcTestResult result =
                flushAs(otherStudent, otherAcademy, body(newSession(), tracks.get("p1:1.0"), seg(0, 1000)));

        assertThat(result).hasStatus(404);
        assertThat(count("select count(*) from listening_session")).isZero();
    }

    @Test
    void 다른_권_반_학생은_404() {
        long l3Class = data.clazz(academyId, "L3-B1", "블루 반", TestData.uniqueAccessCode(), "active");
        long l3Student = data.student(academyId, "최학생");
        data.enroll(l3Class, l3Student);

        assertThat(flushAs(l3Student, academyId, body(newSession(), tracks.get("p1:1.0"), seg(0, 1000))))
                .hasStatus(404);
        assertThat(count("select count(*) from listening_session")).isZero();
    }

    @Test
    void 두_번째_반_권의_트랙도_기록한다() {
        long second = data.clazz(academyId, "L2-B2", "봄 그린 반", TestData.uniqueAccessCode(), "active");
        data.enroll(second, studentId);
        data.session(second, 1, "L2-B2-01", LocalDate.of(2026, 11, 10), "scheduled");
        Map<String, Long> spring = data.lessonContent("L2-B2-01", 1, 1, List.of("1.0"));

        assertThat(flush(body(newSession(), spring.get("p1:1.0"), seg(0, 1000))))
                .hasStatusOk();
        assertThat(count("select count(*) from listening_session")).isEqualTo(1);
    }

    @Test
    void 같은_권_두_반_중_한_반에서_열렸으면_기록한다() {
        long second = data.clazz(academyId, "L2-B1", "토요 반", TestData.uniqueAccessCode(), "active");
        data.enroll(second, studentId);
        data.session(second, 6, "L2-B1-06", LocalDate.of(2026, 11, 9), "scheduled");

        assertThat(flush(body(newSession(), tomorrowTracks.get("p1:1.0"), seg(0, 1000))))
                .hasStatusOk();
    }

    @Test
    void 다른_학원_반의_진도표로는_기록하지_않는다() {
        long otherAcademy = data.academy("다른 학원");
        long other = data.clazz(otherAcademy, "L2-B2", "다른 학원 반", TestData.uniqueAccessCode(), "active");
        data.enroll(other, studentId);
        data.session(other, 1, "L2-B2-01", LocalDate.of(2026, 11, 10), "scheduled");
        Map<String, Long> spring = data.lessonContent("L2-B2-01", 1, 1, List.of("1.0"));

        assertThat(flush(body(newSession(), spring.get("p1:1.0"), seg(0, 1000))))
                .hasStatus(404);
        assertThat(count("select count(*) from listening_session")).isZero();
    }

    @Test
    void 진도표에_없는_회차는_404() {
        Map<String, Long> other = data.lessonContent("L2-B1-07", 1, 1, List.of("1.0"));

        assertThat(flush(body(newSession(), other.get("p1:1.0"), seg(0, 1000)))).hasStatus(404);
        assertThat(count("select count(*) from listening_session")).isZero();
    }

    @Test
    void 내일_회차는_423() {
        MvcTestResult result = flush(body(newSession(), tomorrowTracks.get("p1:1.0"), seg(0, 1000)));

        assertThat(result).hasStatus(423);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("LESSON_LOCKED");
        assertThat(count("select count(*) from listening_session")).isZero();
    }

    @Test
    void 없는_트랙은_404() {
        assertThat(flush(body(newSession(), 999_999_999L, seg(0, 1000)))).hasStatus(404);
        assertThat(count("select count(*) from listening_session")).isZero();
    }

    @Test
    void text_plain_바디_csrf로_통과() {
        MvcTestResult result = mvc.post()
                .uri(URL)
                .contentType(MediaType.TEXT_PLAIN)
                .cookie(cookies.studentAccess(studentId, academyId), AuthCookieSupport.csrf())
                .content(bodyWith(newSession(), tracks.get("p1:1.0"), AuthCookieSupport.CSRF_VALUE, seg(0, 1000)))
                .exchange();

        assertThat(result).hasStatusOk();
    }

    @Test
    void csrf가_없으면_403() {
        MvcTestResult result = mvc.post()
                .uri(URL)
                .contentType(MediaType.TEXT_PLAIN)
                .cookie(cookies.studentAccess(studentId, academyId), AuthCookieSupport.csrf())
                .content(body(newSession(), tracks.get("p1:1.0"), seg(0, 1000)))
                .exchange();

        assertThat(result).hasStatus(403);
        assertThat(count("select count(*) from listening_session")).isZero();
    }

    @Test
    void csrf가_다르면_403() {
        MvcTestResult wrongBody = mvc.post()
                .uri(URL)
                .contentType(MediaType.TEXT_PLAIN)
                .cookie(cookies.studentAccess(studentId, academyId), AuthCookieSupport.csrf())
                .content(bodyWith(newSession(), tracks.get("p1:1.0"), "wrong", seg(0, 1000)))
                .exchange();
        MvcTestResult wrongHeader = mvc.post()
                .uri(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .cookie(cookies.studentAccess(studentId, academyId), AuthCookieSupport.csrf())
                .header(AuthCookieSupport.CSRF_HEADER, "wrong")
                .content(body(newSession(), tracks.get("p1:1.0"), seg(0, 1000)))
                .exchange();
        MvcTestResult noCookie = mvc.post()
                .uri(URL)
                .contentType(MediaType.TEXT_PLAIN)
                .cookie(cookies.studentAccess(studentId, academyId))
                .content(bodyWith(newSession(), tracks.get("p1:1.0"), AuthCookieSupport.CSRF_VALUE, seg(0, 1000)))
                .exchange();

        MvcTestResult wrongHeaderRightBody = mvc.post()
                .uri(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .cookie(cookies.studentAccess(studentId, academyId), AuthCookieSupport.csrf())
                .header(AuthCookieSupport.CSRF_HEADER, "wrong")
                .content(bodyWith(newSession(), tracks.get("p1:1.0"), AuthCookieSupport.CSRF_VALUE, seg(0, 1000)))
                .exchange();

        assertThat(wrongBody).hasStatus(403);
        assertThat(wrongHeaderRightBody).as("헤더가 있으면 헤더로만 판단").hasStatus(403);
        assertThat(wrongHeader).hasStatus(403);
        assertThat(noCookie).as("csrf 쿠키 없음").hasStatus(403);
    }

    @Test
    void 쿠키가_없으면_401() {
        MvcTestResult result = mvc.post()
                .uri(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(newSession(), tracks.get("p1:1.0"), seg(0, 1000)))
                .exchange();

        assertThat(result).hasStatus(401);
    }

    @Test
    void 교사는_403() {
        long teacher = data.staff(academyId, "교사", "teacher");

        MvcTestResult result = mvc.post()
                .uri(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .cookie(cookies.access(teacher, UserRole.TEACHER, academyId), AuthCookieSupport.csrf())
                .header(AuthCookieSupport.CSRF_HEADER, AuthCookieSupport.CSRF_VALUE)
                .content(body(newSession(), tracks.get("p1:1.0"), seg(0, 1000)))
                .exchange();

        assertThat(result).hasStatus(403);
    }

    @Test
    void 다른_콘텐츠_타입은_415() {
        MvcTestResult result = mvc.post()
                .uri(URL)
                .contentType(MediaType.APPLICATION_XML)
                .cookie(cookies.studentAccess(studentId, academyId), AuthCookieSupport.csrf())
                .header(AuthCookieSupport.CSRF_HEADER, AuthCookieSupport.CSRF_VALUE)
                .content(body(newSession(), tracks.get("p1:1.0"), seg(0, 1000)))
                .exchange();

        assertThat(result).hasStatus(415);
    }

    @Test
    void flush_뒤_회차_상세에_들은_구간이_보인다() {
        flush(body(newSession(), tracks.get("p1:1.0"), seg(0, 3000)));

        MvcTestResult detail = mvc.get()
                .uri("/api/v1/student/lessons/L2-B1-05")
                .cookie(cookies.studentAccess(studentId, academyId))
                .exchange();

        assertThat(detail)
                .bodyJson()
                .extractingPath("$.passages[0].speeds[1].heardRanges")
                .isEqualTo(List.of(List.of(0, 3000)));
    }

    @Test
    void 바디가_null이면_400() {
        MvcTestResult result = flush("null");

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION");
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("body");
    }

    @Test
    void 트랙_길이를_넘는_부분은_총_재생_시간에_더하지_않는다() {
        long one = tracks.get("p1:1.0");
        String session = newSession();

        flush(body(session, one, seg(6000, 7000), seg(6600, 7400)));

        assertThat(trackRow(one))
                .containsEntry("merged_ranges", "[[6000, 6500]]")
                .containsEntry("total_play_ms", 500);
    }

    @Test
    void 완료한_트랙에_잘못된_세그먼트만_오면_현재_값_그대로() {
        long one = tracks.get("p1:1.0");
        flush(body(newSession(), one, seg(0, 6500)));
        Map<String, Object> before = trackRow(one);

        MvcTestResult result = flush(body(newSession(), one, seg(-5, 100)));

        var json = assertThat(result).bodyJson();
        json.extractingPath("$.track.completed").isEqualTo(true);
        json.extractingPath("$.track.coverage").isEqualTo(1);
        json.extractingPath("$.dropped").isEqualTo(1);
        assertThat(trackRow(one))
                .containsEntry("merged_ranges", before.get("merged_ranges"))
                .containsEntry("total_play_ms", before.get("total_play_ms"))
                .containsEntry("session_count", before.get("session_count"));
    }

    @Test
    void 회차_완료_시각은_처음_값을_유지한다() {
        flush(body(newSession(), tracks.get("p1:1.0"), seg(0, 6500)));
        flush(body(newSession(), tracks.get("p2:1.0"), seg(0, 6500)));
        clock.set(NOON.plusSeconds(60));

        flush(body(newSession(), tracks.get("p1:0.8"), seg(0, 1000)));

        assertThat(jdbc.queryForMap(
                        "select status, completed_at from lesson_progress where student_id = ? and lesson_id = ?",
                        studentId,
                        data.lessonId("L2-B1-05")))
                .containsEntry("status", "completed")
                .hasEntrySatisfying("completed_at", v -> assertThat(v).hasToString("2026-11-10T03:00"));
    }

    @Test
    void 첫_flush가_모두_버려진_세션도_나중에_세션_수_1() {
        long one = tracks.get("p1:1.0");
        String session = newSession();
        flush(body(session, one, seg(-5, 100)));

        flush(body(session, one, seg(0, 1000)));

        assertThat(trackRow(one)).containsEntry("session_count", 1).containsEntry("total_play_ms", 1000);
    }

    @Test
    void 서울_자정_직전은_내일_회차_423() {
        clock.set(Instant.parse("2026-11-10T14:59:59Z")); // 서울 11/10 23:59:59

        assertThat(flush(body(newSession(), tomorrowTracks.get("p1:1.0"), seg(0, 1000))))
                .hasStatus(423);
    }

    @Test
    void 서울_자정이_지나면_내일_회차가_열린다() {
        clock.set(Instant.parse("2026-11-10T15:00:30Z")); // 서울 11/11 00:00:30, UTC는 아직 11/10

        assertThat(flush(body(newSession(), tomorrowTracks.get("p1:1.0"), seg(0, 1000))))
                .hasStatusOk();
    }

    @Test
    void 정지_학원_학생은_401_행이_안_생긴다() {
        jdbc.update("update academy set status = 'suspended' where id = ?", academyId);

        assertThat(flush(body(newSession(), tracks.get("p1:1.0"), seg(0, 1000))))
                .hasStatus(401);
        assertThat(count("select count(*) from listening_session")).isZero();
    }
}
