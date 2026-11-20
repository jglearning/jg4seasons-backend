package com.jglearning.jg4seasons.listening.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.MutableClock;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.auth.AuthCookieSupport;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
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
 * 3.1 flush — 목소리를 바꿔 들어도 지문 청취를 합친다(ADR 0034). 오늘 = 서울 2026-11-10, 반 L2-B1, 5회차 지문 3개 × 문장 3개.
 *
 * <p>목소리마다 음원 길이가 다르다: us_w·us_m은 10초(문장 3000ms × 3, 간격 500ms), uk_m은 14초(문장 4000·2000·6000ms, 간격 1000ms). 기준 시간축은
 * 필수 목소리 중 첫 트랙인 us_w. uk_m의 6000~14000(문장 1의 뒤 절반부터 끝)은 us_w의 5000~10000이고, uk_m의 0~5800은 us_w의 0~4700이다.
 */
@IntegrationTest
@Transactional
class FlushCombineTest {

    static final String URL = "/api/v1/listening/flush";
    static final String CODE = "L2-B1-05";
    static final Instant NOON = Instant.parse("2026-11-10T03:00:00Z");
    static final int[] TEN_SECONDS = {0, 3_000, 3_500, 6_500, 7_000, 10_000};
    static final int[] FOURTEEN_SECONDS = {0, 4_000, 5_000, 7_000, 8_000, 14_000};

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
        clock.set(NOON);
        data = new TestData(jdbc);
        academyId = data.academy("테스트 학원");
        long classId = data.clazz(academyId, "L2-B1", "겨울 그린 A반", TestData.uniqueAccessCode(), "active");
        studentId = data.student(academyId, "김학생");
        data.enroll(classId, studentId);
        data.session(classId, 5, CODE, LocalDate.of(2026, 11, 10), "scheduled");
        LinkedHashMap<String, int[]> voices = new LinkedHashMap<>();
        voices.put("us_w", TEN_SECONDS);
        voices.put("uk_m", FOURTEEN_SECONDS);
        voices.put("us_m", TEN_SECONDS);
        tracks = data.lessonContent(CODE, 3, voices);
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    private MvcTestResult flush(String trackKey, int[]... segments) {
        String list = Arrays.stream(segments)
                .map(s -> "{\"startMs\":" + s[0] + ",\"endMs\":" + s[1] + ",\"recordedAt\":\"2026-11-10T02:59:59Z\"}")
                .collect(Collectors.joining(",", "[", "]"));
        String json = "{\"clientSessionId\":\"" + UUID.randomUUID() + "\",\"trackId\":" + tracks.get(trackKey)
                + ",\"startedAt\":\"2026-11-10T02:59:00Z\",\"userAgent\":\"test\",\"segments\":" + list
                + ",\"final\":false}";
        return mvc.post()
                .uri(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .cookie(cookies.studentAccess(studentId, academyId), AuthCookieSupport.csrf())
                .header(AuthCookieSupport.CSRF_HEADER, AuthCookieSupport.CSRF_VALUE)
                .content(json)
                .exchange();
    }

    static int[] seg(int start, int end) {
        return new int[] {start, end};
    }

    private long passageId(int no) {
        return jdbc.queryForObject(
                "select id from passage where lesson_id = ? and no = ?", Long.class, data.lessonId(CODE), no);
    }

    private Map<String, Object> passageRow(int no) {
        return jdbc.queryForMap(
                "select ref_track_id, ref_duration_ms, merged_ranges, covered_ms, coverage, completed_at"
                        + " from passage_progress where student_id = ? and passage_id = ?",
                studentId,
                passageId(no));
    }

    private int passageRows() {
        return jdbc.queryForObject(
                "select count(*) from passage_progress where student_id = ?", Integer.class, studentId);
    }

    /** 이 레벨 규칙의 기준 값 — 테스트에 숫자를 적지 않고 DB에서 읽는다 */
    private BigDecimal threshold() {
        return jdbc.queryForObject(
                "select r.coverage_threshold from completion_rule r join book b on b.level_id = r.level_id"
                        + " where b.code = 'L2-B1'",
                BigDecimal.class);
    }

    private void turnCombineOff() {
        jdbc.update(
                "update completion_rule set required_tracks = ?"
                        + " where level_id = (select level_id from book where code = 'L2-B1')",
                "[{\"kind\":\"passage\",\"eachPassage\":true,\"anyOf\":[\"us_w\",\"us_m\",\"uk_w\",\"uk_m\"]}]");
    }

    @Test
    void 두_목소리로_나눠_들으면_지문_완료() {
        MvcTestResult first = flush("p1:us_w", seg(0, 5_000));
        MvcTestResult second = flush("p1:uk_m", seg(6_000, 14_000));

        var half = assertThat(first).bodyJson();
        half.extractingPath("$.passage.no").isEqualTo(1);
        half.extractingPath("$.passage.coverage").isEqualTo(0.5);
        half.extractingPath("$.passage.completed").isEqualTo(false);
        var json = assertThat(second).bodyJson();
        json.extractingPath("$.track.completed").as("uk_m 트랙만으로는 미완료").isEqualTo(false);
        json.extractingPath("$.track.coverage").isEqualTo(0.5714); // 8000 / 14000 내림
        json.extractingPath("$.passage.no").isEqualTo(1);
        json.extractingPath("$.passage.coverage").isEqualTo(1);
        json.extractingPath("$.passage.completed").isEqualTo(true);
        json.extractingPath("$.lesson.requiredDone").isEqualTo(1);
        json.extractingPath("$.lesson.requiredTotal").isEqualTo(3);
        json.extractingPath("$.lesson.status").isEqualTo("in_progress");
        assertThat(passageRow(1))
                .containsEntry("ref_track_id", tracks.get("p1:us_w"))
                .containsEntry("ref_duration_ms", 10_000)
                .containsEntry("merged_ranges", "[[0, 10000]]")
                .containsEntry("covered_ms", 10_000);
        assertThat(passageRow(1).get("completed_at")).hasToString("2026-11-10T03:00");
        assertThat(jdbc.queryForList(
                        "select merged_ranges from track_progress where student_id = ? order by track_id",
                        String.class,
                        studentId))
                .as("목소리별 집계는 각자 그대로")
                .containsExactly("[[0, 5000]]", "[[6000, 14000]]");
        assertThat(passageRows()).isEqualTo(1);
        assertThat(jdbc.queryForMap(
                        "select required_done, passages_done, status from lesson_progress where student_id = ?",
                        studentId))
                .as("교사 매트릭스가 읽는 회차 집계")
                .containsEntry("required_done", 1)
                .containsEntry("passages_done", 1)
                .containsEntry("status", "in_progress");
    }

    @Test
    void 같은_자리를_두_목소리로_들어도_늘지_않는다() {
        flush("p1:us_w", seg(0, 5_000));

        MvcTestResult result = flush("p1:uk_m", seg(0, 5_800));

        var json = assertThat(result).bodyJson();
        json.extractingPath("$.passage.coverage").isEqualTo(0.5);
        json.extractingPath("$.passage.completed").isEqualTo(false);
        json.extractingPath("$.lesson.requiredDone").isEqualTo(0);
        assertThat(passageRow(1)).containsEntry("merged_ranges", "[[0, 5000]]").containsEntry("covered_ms", 5_000);
    }

    @Test
    void 한_목소리로_기준만큼_들으면_합친_값이_기준보다_낮아도_지금처럼_완료() {
        // uk_m 0~5300 + 6700~14000 = 12600ms = 14000의 0.9 → 그 트랙은 완료.
        // 기준 시간축(us_w)으로 옮기면 [0,3950] + [6050,10000] = 7900ms = 0.79 — 합친 값만 보면 미완료다(바닥 규칙이 지킨다)
        MvcTestResult result = flush("p1:uk_m", seg(0, 5_300), seg(6_700, 14_000));

        var json = assertThat(result).bodyJson();
        json.extractingPath("$.track.completed").isEqualTo(true);
        json.extractingPath("$.track.coverage").isEqualTo(0.9);
        json.extractingPath("$.passage.completed").as("목소리 하나의 완료는 그대로 인정").isEqualTo(true);
        json.extractingPath("$.passage.coverage")
                .as("완료인데 기준보다 낮은 숫자(0.79)가 나가지 않는다")
                .isEqualTo(0.9);
        json.extractingPath("$.lesson.requiredDone").isEqualTo(1);
        assertThat(passageRow(1))
                .containsEntry("merged_ranges", "[[0, 3950], [6050, 10000]]")
                .containsEntry("covered_ms", 7_900);
        assertThat(passageRow(1).get("completed_at")).isNotNull();
        assertThat(new BigDecimal("0.9")).as("이 테스트의 전제: 0.9는 이 레벨의 기준").isEqualByComparingTo(threshold());
    }

    private static String body(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString();
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void 합산이_꺼진_규칙이면_합치지_않고_지문_집계_행도_만들지_않는다() {
        turnCombineOff();
        flush("p1:us_w", seg(0, 5_000));

        MvcTestResult result = flush("p1:uk_m", seg(6_000, 14_000));

        var json = assertThat(result).bodyJson();
        json.extractingPath("$.passage.completed").isEqualTo(false);
        json.extractingPath("$.passage.coverage").as("목소리 하나의 최댓값").isEqualTo(0.5714);
        json.extractingPath("$.lesson.requiredDone").isEqualTo(0);
        assertThat(passageRows()).isZero();
    }

    @Test
    void 필수가_아닌_목소리로_들은_구간은_합치지_않는다() {
        jdbc.update(
                "update completion_rule set required_tracks = ?"
                        + " where level_id = (select level_id from book where code = 'L2-B1')",
                "[{\"kind\":\"passage\",\"eachPassage\":true,\"anyOf\":[\"us_w\"],\"combine\":true}]");
        flush("p1:us_w", seg(0, 5_000));

        MvcTestResult result = flush("p1:uk_m", seg(6_000, 14_000));

        var json = assertThat(result).bodyJson();
        json.extractingPath("$.passage.completed").isEqualTo(false);
        json.extractingPath("$.passage.coverage").isEqualTo(0.5);
        assertThat(passageRow(1)).containsEntry("merged_ranges", "[[0, 5000]]");
    }

    @Test
    void 표가_생기기_전_기록만_있어도_완료_지문_수가_줄지_않고_다음_flush에서_합산에_들어온다() {
        // 지문 1은 us_w로, 지문 2는 uk_m으로 이미 완료 — passage_progress 행은 없다(V10 전의 기록)
        insertCompletedTrack("p1:us_w", 10_000);
        insertCompletedTrack("p2:uk_m", 14_000);
        jdbc.update(
                "insert into lesson_progress (student_id, lesson_id, status, required_total, required_done,"
                        + " passages_total, passages_done) values (?, ?, 'in_progress', 3, 2, 3, 2)",
                studentId,
                data.lessonId(CODE));

        MvcTestResult third = flush("p3:us_w", seg(0, 1_000));

        assertThat(third).bodyJson().extractingPath("$.lesson.requiredDone").isEqualTo(2);
        assertThat(third).bodyJson().extractingPath("$.passage.completed").isEqualTo(false);

        MvcTestResult again = flush("p1:uk_m", seg(0, 1_000));

        var json = assertThat(again).bodyJson();
        json.extractingPath("$.passage.completed").isEqualTo(true);
        json.extractingPath("$.passage.coverage").isEqualTo(1);
        json.extractingPath("$.lesson.requiredDone").isEqualTo(2);
        assertThat(passageRow(1)).as("옛 us_w 기록이 합산에 들어온다").containsEntry("merged_ranges", "[[0, 10000]]");
        assertThat(passageRow(1).get("completed_at")).isNotNull();
    }

    private void insertCompletedTrack(String trackKey, int durationMs) {
        jdbc.update(
                "insert into track_progress (student_id, track_id, merged_ranges, covered_ms, coverage, completed_at,"
                        + " total_play_ms, session_count, last_played_at)"
                        + " values (?, ?, ?, ?, 1, '2026-11-09 03:00:00', ?, 1, '2026-11-09 03:00:00')",
                studentId,
                tracks.get(trackKey),
                "[[0, " + durationMs + "]]",
                durationMs,
                durationMs);
    }

    @Test
    void 지문_완료는_한_번_되면_규칙이_바뀌어도_유지한다() {
        flush("p1:us_w", seg(0, 5_000));
        flush("p1:uk_m", seg(6_000, 14_000));
        turnCombineOff();

        MvcTestResult result = flush("p1:us_m", seg(0, 500));

        var json = assertThat(result).bodyJson();
        json.extractingPath("$.passage.completed").isEqualTo(true);
        json.extractingPath("$.lesson.requiredDone").isEqualTo(1);
        assertThat(passageRow(1).get("completed_at")).hasToString("2026-11-10T03:00");
    }

    @Test
    void 버려진_세그먼트만_온_flush는_지문_집계를_건드리지_않고_지금_값을_준다() {
        flush("p1:us_w", seg(0, 5_000));
        flush("p1:uk_m", seg(6_000, 14_000));
        clock.set(NOON.plusSeconds(60));

        MvcTestResult result = flush("p1:us_m", seg(9_000, 8_000));

        var json = assertThat(result).bodyJson();
        json.extractingPath("$.dropped").isEqualTo(1);
        json.extractingPath("$.passage.no").isEqualTo(1);
        json.extractingPath("$.passage.coverage").isEqualTo(1);
        json.extractingPath("$.passage.completed").isEqualTo(true);
        assertThat(passageRow(1)).containsEntry("merged_ranges", "[[0, 10000]]");
        assertThat(passageRow(1).get("completed_at")).as("처음 완료 시각 그대로").hasToString("2026-11-10T03:00");
    }

    @Test
    void 안_들은_지문에_버려진_세그먼트만_오면_0이고_미완료() {
        MvcTestResult result = flush("p2:uk_m", seg(9_000, 8_000));

        var json = assertThat(result).bodyJson();
        json.extractingPath("$.passage.no").isEqualTo(2);
        json.extractingPath("$.passage.coverage").isEqualTo(0);
        json.extractingPath("$.passage.completed").isEqualTo(false);
        assertThat(passageRows()).isZero();
    }

    @Test
    void 모든_지문을_섞어_들으면_회차_완료() {
        MvcTestResult last = null;
        for (int no = 1; no <= 3; no++) {
            flush("p" + no + ":us_w", seg(0, 5_000));
            last = flush("p" + no + ":uk_m", seg(6_000, 14_000));
        }

        var json = assertThat(last).bodyJson();
        json.extractingPath("$.lesson.status").isEqualTo("completed");
        json.extractingPath("$.lesson.requiredDone").isEqualTo(3);
        assertThat(jdbc.queryForObject(
                        "select count(*) from track_progress where student_id = ? and completed_at is not null",
                        Integer.class,
                        studentId))
                .as("완료한 목소리 트랙은 하나도 없다")
                .isZero();
    }

    @Test
    void 문장_중간에_목소리를_바꿔도_이어진다() {
        // us_w로 문장 1의 40% 지점(4700)까지, uk_m으로 같은 자리(5800)부터 끝까지
        flush("p1:us_w", seg(0, 4_700));

        MvcTestResult result = flush("p1:uk_m", seg(5_800, 14_000));

        assertThat(result).bodyJson().extractingPath("$.passage.coverage").isEqualTo(1);
        assertThat(passageRow(1)).containsEntry("merged_ranges", "[[0, 10000]]");
    }

    @Test
    void 지문_완료는_기준이_올라도_유지한다() {
        // us_w 0~5000 + uk_m 6000~13000(= us_w의 5000~9500) → 합친 값 0.95로 완료
        flush("p1:us_w", seg(0, 5_000));
        MvcTestResult done = flush("p1:uk_m", seg(6_000, 13_000));
        assertThat(done).bodyJson().extractingPath("$.passage.coverage").isEqualTo(0.95);
        assertThat(done).bodyJson().extractingPath("$.passage.completed").isEqualTo(true);
        jdbc.update("update completion_rule set coverage_threshold = 1.000"
                + " where level_id = (select level_id from book where code = 'L2-B1')");
        clock.set(NOON.plusSeconds(60));

        MvcTestResult result = flush("p1:us_m", seg(0, 500));

        var json = assertThat(result).bodyJson();
        json.extractingPath("$.passage.completed")
                .as("지금 기준(1.0)으로는 0.95가 미완료여도 유지")
                .isEqualTo(true);
        json.extractingPath("$.passage.coverage").isEqualTo(0.95);
        json.extractingPath("$.lesson.requiredDone").as("완료 지문 수가 줄지 않는다").isEqualTo(1);
        assertThat(passageRow(1).get("completed_at")).as("처음 완료 시각").hasToString("2026-11-10T03:00");
    }

    @Test
    void 필수가_아닌_목소리가_앞에_있으면_그다음_필수_목소리가_기준이다() {
        // 트랙 순서는 us_w·uk_m·us_m. us_w가 필수가 아니면 기준 시간축은 uk_m(14초)
        jdbc.update(
                "update completion_rule set required_tracks = ?"
                        + " where level_id = (select level_id from book where code = 'L2-B1')",
                "[{\"kind\":\"passage\",\"eachPassage\":true,\"anyOf\":[\"uk_m\",\"us_m\"],\"combine\":true}]");
        flush("p1:uk_m", seg(0, 5_800));

        MvcTestResult result = flush("p1:us_m", seg(4_700, 10_000)); // us_m의 4700~10000 = uk_m의 5800~14000

        var json = assertThat(result).bodyJson();
        json.extractingPath("$.passage.coverage").isEqualTo(1);
        json.extractingPath("$.passage.completed").isEqualTo(true);
        assertThat(passageRow(1))
                .containsEntry("ref_track_id", tracks.get("p1:uk_m"))
                .containsEntry("ref_duration_ms", 14_000)
                .containsEntry("merged_ranges", "[[0, 14000]]");
    }

    @Test
    void 필수_목소리_트랙이_없는_지문은_지문_집계_없이_전처럼_기록된다() {
        // 이 회차에는 uk_w 트랙이 없다. 합산이 생기기 전에도 이런 지문은 "완료될 수 없음"이었고 flush는 됐다
        jdbc.update(
                "update completion_rule set required_tracks = ?"
                        + " where level_id = (select level_id from book where code = 'L2-B1')",
                "[{\"kind\":\"passage\",\"eachPassage\":true,\"anyOf\":[\"uk_w\"],\"combine\":true}]");

        MvcTestResult result = flush("p1:us_w", seg(0, 5_000));

        assertThat(result).hasStatusOk();
        var json = assertThat(result).bodyJson();
        json.extractingPath("$.track.coverage").isEqualTo(0.5);
        json.extractingPath("$.passage.completed").isEqualTo(false);
        json.extractingPath("$.passage.coverage").isEqualTo(0);
        json.extractingPath("$.lesson.requiredDone").isEqualTo(0);
        assertThat(passageRows()).isZero();
        assertThat(jdbc.queryForObject(
                        "select merged_ranges from track_progress where student_id = ?", String.class, studentId))
                .as("원본과 목소리별 집계는 저장된다")
                .isEqualTo("[[0, 5000]]");
    }
}
