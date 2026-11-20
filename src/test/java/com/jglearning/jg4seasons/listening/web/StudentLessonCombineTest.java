package com.jglearning.jg4seasons.listening.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.MutableClock;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.auth.AuthCookieSupport;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
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
 * 2.3 회차 상세 — 지문 합산 청취율·완료와, 합친 구간을 목소리마다 그 목소리의 시간축으로 옮긴 combinedRanges(ADR 0034).
 *
 * <p>픽스처는 FlushCombineTest와 같다: 지문 2개 × 문장 3개, 목소리 순서 us_w(10초)·uk_m(14초)·us_m(10초). 기준 시간축은 us_w. us_w의 문장 1은
 * 3500~6500, uk_m의 문장 1은 5000~7000.
 */
@IntegrationTest
@Transactional
class StudentLessonCombineTest {

    static final String CODE = "L2-B1-05";
    static final Instant NOON = Instant.parse("2026-11-10T03:00:00Z");
    static final int US_W = 0;
    static final int UK_M = 1;
    static final int US_M = 2;

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
        voices.put("us_w", FlushCombineTest.TEN_SECONDS);
        voices.put("uk_m", FlushCombineTest.FOURTEEN_SECONDS);
        voices.put("us_m", FlushCombineTest.TEN_SECONDS);
        tracks = data.lessonContent(CODE, 2, voices);
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    private void flush(String trackKey, int startMs, int endMs) {
        String json = "{\"clientSessionId\":\"" + UUID.randomUUID() + "\",\"trackId\":" + tracks.get(trackKey)
                + ",\"startedAt\":\"2026-11-10T02:59:00Z\",\"userAgent\":\"test\",\"segments\":[{\"startMs\":" + startMs
                + ",\"endMs\":" + endMs + ",\"recordedAt\":\"2026-11-10T02:59:59Z\"}],\"final\":false}";
        assertThat(mvc.post()
                        .uri("/api/v1/listening/flush")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(cookies.studentAccess(studentId, academyId), AuthCookieSupport.csrf())
                        .header(AuthCookieSupport.CSRF_HEADER, AuthCookieSupport.CSRF_VALUE)
                        .content(json)
                        .exchange())
                .hasStatusOk();
    }

    /** 회차 상세 응답 본문 */
    private String detail() {
        MvcTestResult result = mvc.get()
                .uri("/api/v1/student/lessons/" + CODE)
                .cookie(cookies.studentAccess(studentId, academyId))
                .exchange();
        assertThat(result).hasStatusOk();
        try {
            return result.getResponse().getContentAsString();
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static <T> T read(String json, String path) {
        return JsonPath.read(json, path);
    }

    private static List<List<Integer>> ranges(String json, int passage, int voice, String field) {
        return read(json, "$.passages[" + passage + "].voices[" + voice + "]." + field);
    }

    private static BigDecimal number(String json, String path) {
        return new BigDecimal(String.valueOf(JsonPath.<Object>read(json, path)));
    }

    @Test
    void 섞어_들은_지문은_합산_청취율과_완료를_주고_목소리별_값은_그대로다() {
        flush("p1:us_w", 0, 5_000);
        flush("p1:uk_m", 6_000, 14_000);

        String json = detail();

        assertThat(number(json, "$.passages[0].coverage")).isEqualByComparingTo("1");
        assertThat(JsonPath.<Boolean>read(json, "$.passages[0].completed")).isTrue();
        assertThat(JsonPath.<Integer>read(json, "$.completion.passagesDone")).isEqualTo(1);
        assertThat(JsonPath.<Boolean>read(json, "$.passages[0].voices[" + US_W + "].completed"))
                .as("목소리 하나만으로는 미완료")
                .isFalse();
        assertThat(JsonPath.<Boolean>read(json, "$.passages[0].voices[" + UK_M + "].completed"))
                .isFalse();
        assertThat(number(json, "$.passages[0].voices[" + US_W + "].coverage")).isEqualByComparingTo("0.5");
        assertThat(ranges(json, 0, US_W, "heardRanges"))
                .as("heardRanges는 그 목소리로 직접 들은 구간 그대로")
                .containsExactly(List.of(0, 5_000));
        assertThat(ranges(json, 0, UK_M, "heardRanges")).containsExactly(List.of(6_000, 14_000));
        assertThat(ranges(json, 0, US_M, "heardRanges")).isEmpty();
    }

    @Test
    void combinedRanges는_합친_구간을_목소리마다_그_목소리의_시간축으로_준다() {
        flush("p1:us_w", 0, 5_000);
        flush("p1:uk_m", 6_000, 14_000);

        String json = detail();

        assertThat(ranges(json, 0, US_W, "combinedRanges")).containsExactly(List.of(0, 10_000));
        assertThat(ranges(json, 0, UK_M, "combinedRanges")).containsExactly(List.of(0, 14_000));
        assertThat(ranges(json, 0, US_M, "combinedRanges"))
                .as("한 번도 안 고른 목소리에도 합친 구간이 보인다")
                .containsExactly(List.of(0, 10_000));
    }

    @Test
    void 문장_일부만_들으면_다른_목소리에서는_같은_문장의_같은_자리다() {
        // us_w의 문장 1의 0~40%(3500~4700) = uk_m의 문장 1의 0~40%(5000~5800)
        flush("p1:us_w", 3_500, 4_700);

        String json = detail();

        assertThat(ranges(json, 0, US_W, "combinedRanges")).containsExactly(List.of(3_500, 4_700));
        assertThat(ranges(json, 0, UK_M, "combinedRanges")).containsExactly(List.of(5_000, 5_800));
        assertThat(ranges(json, 0, UK_M, "heardRanges")).isEmpty();
        assertThat(number(json, "$.passages[0].coverage")).isEqualByComparingTo("0.12");
        assertThat(JsonPath.<Boolean>read(json, "$.passages[0].completed")).isFalse();
        for (int voice : new int[] {US_W, UK_M, US_M}) {
            int duration = read(json, "$.passages[0].voices[" + voice + "].durationMs");
            for (List<Integer> range : ranges(json, 0, voice, "combinedRanges")) {
                assertThat(range.get(1)).as("목소리 %d의 구간은 그 목소리 길이 안", voice).isLessThanOrEqualTo(duration);
            }
        }
    }

    @Test
    void 한_목소리로_완료한_지문의_coverage는_기준보다_낮지_않다() {
        flush("p1:uk_m", 0, 12_600); // 14000의 0.9 — 기준 시간축으로 옮긴 값은 이와 다를 수 있다

        String json = detail();

        assertThat(JsonPath.<Boolean>read(json, "$.passages[0].completed")).isTrue();
        assertThat(number(json, "$.passages[0].coverage"))
                .isGreaterThanOrEqualTo(number(json, "$.completion.threshold"));
    }

    @Test
    void 기준_트랙_길이가_저장된_값과_다르면_옛_구간을_내보내지_않는다() {
        flush("p1:us_w", 0, 5_000);
        flush("p1:uk_m", 6_000, 14_000);
        // 콘텐츠를 다시 넣어 기준 트랙(us_w)의 길이가 바뀐 것과 같은 상태
        assertThat(jdbc.update("update passage_progress set ref_duration_ms = 9000 where student_id = ?", studentId))
                .isEqualTo(1);

        String json = detail();

        assertThat(JsonPath.<Boolean>read(json, "$.passages[0].completed"))
                .as("완료는 한 번 되면 유지")
                .isTrue();
        assertThat(number(json, "$.passages[0].coverage"))
                .as("옛 시간축의 합친 값(1) 대신 목소리 하나의 최댓값")
                .isEqualByComparingTo("0.5714");
        assertThat(ranges(json, 0, US_W, "combinedRanges")).containsExactly(List.of(0, 5_000));
        assertThat(ranges(json, 0, UK_M, "combinedRanges")).containsExactly(List.of(6_000, 14_000));
        assertThat(ranges(json, 0, US_M, "combinedRanges")).isEmpty();
    }

    @Test
    void 안_들은_지문은_0이고_빈_구간() {
        flush("p1:us_w", 0, 5_000);

        String json = detail();

        assertThat(number(json, "$.passages[1].coverage")).isEqualByComparingTo("0");
        assertThat(JsonPath.<Boolean>read(json, "$.passages[1].completed")).isFalse();
        for (int voice : new int[] {US_W, UK_M, US_M}) {
            assertThat(ranges(json, 1, voice, "combinedRanges")).isEmpty();
        }
    }

    @Test
    void 지문_집계가_없는_옛_기록은_목소리별_집계로_나간다() {
        jdbc.update(
                "insert into track_progress (student_id, track_id, merged_ranges, covered_ms, coverage, completed_at,"
                        + " total_play_ms, session_count, last_played_at)"
                        + " values (?, ?, '[[0, 10000]]', 10000, 1, '2026-11-09 03:00:00', 10000, 1,"
                        + " '2026-11-09 03:00:00')",
                studentId,
                tracks.get("p1:us_w"));

        String json = detail();

        assertThat(JsonPath.<Boolean>read(json, "$.passages[0].completed")).isTrue();
        assertThat(number(json, "$.passages[0].coverage")).isEqualByComparingTo("1");
        assertThat(ranges(json, 0, US_W, "combinedRanges")).containsExactly(List.of(0, 10_000));
        assertThat(ranges(json, 0, UK_M, "combinedRanges"))
                .as("지문 집계가 없으면 그 목소리의 heardRanges와 같다")
                .isEmpty();
    }

    @Test
    void 합산이_꺼진_규칙이면_combinedRanges는_heardRanges와_같다() {
        jdbc.update(
                "update completion_rule set required_tracks = ?"
                        + " where level_id = (select level_id from book where code = 'L2-B1')",
                "[{\"kind\":\"passage\",\"eachPassage\":true,\"anyOf\":[\"us_w\",\"us_m\",\"uk_w\",\"uk_m\"]}]");
        flush("p1:us_w", 0, 5_000);
        flush("p1:uk_m", 6_000, 14_000);

        String json = detail();

        assertThat(JsonPath.<Boolean>read(json, "$.passages[0].completed")).isFalse();
        assertThat(number(json, "$.passages[0].coverage")).isEqualByComparingTo("0.5714");
        assertThat(ranges(json, 0, US_W, "combinedRanges")).containsExactly(List.of(0, 5_000));
        assertThat(ranges(json, 0, UK_M, "combinedRanges")).containsExactly(List.of(6_000, 14_000));
    }

    @Test
    void 지문에_새_키가_있다() {
        String json = detail();

        assertThat(JsonPath.<Map<String, Object>>read(json, "$.passages[0]"))
                .containsOnlyKeys("no", "title", "wordCount", "coverage", "completed", "sentences", "voices");
        assertThat(JsonPath.<Map<String, Object>>read(json, "$.passages[0].voices[0]"))
                .containsKeys("heardRanges", "combinedRanges", "coverage", "completed", "timings");
    }
}
