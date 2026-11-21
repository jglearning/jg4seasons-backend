package com.jglearning.jg4seasons.listening.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.auth.api.AcademyStudents;
import com.jglearning.jg4seasons.content.api.LessonCatalog;
import com.jglearning.jg4seasons.content.api.LessonContent;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 교사 화면용 조회(이슈 #62): 학생 묶음 × 회차들의 트랙 집계와 지문 요약. 지문 요약은 학생 화면(2.3)과 같은 계산(ADR 0034 결정 5·7)을 거친다 — 저장 행을 그대로 내보내지
 * 않는다.
 */
@IntegrationTest
@Transactional
class ListeningDetailQueryTest {

    /** 지문 길이: 문장 2개 × 3000 + 간격 500 */
    static final int PASSAGE_MS = 2 * TestData.SENTENCE_MS + TestData.GAP_MS;

    @Autowired
    ListeningDetailQuery query;

    @Autowired
    TrackProgressQuery trackQuery;

    @Autowired
    LessonCatalog catalog;

    @Autowired
    JdbcTemplate jdbc;

    TestData data;
    long academyId;
    long a;
    long b;
    Map<String, Long> tracks;

    @BeforeEach
    void setUp() {
        data = new TestData(jdbc);
        academyId = data.academy("테스트 학원");
        a = data.student(academyId, "김학생");
        b = data.student(academyId, "이학생");
        tracks = data.lessonContent("L2-B1-05", 2, 2, List.of("us_w", "us_m", "uk_w"));
    }

    private LessonContent content(String code) {
        return catalog.findByCode(code).orElseThrow();
    }

    private AcademyStudents students(Long... ids) {
        return AcademyStudents.of(academyId, List.of(ids));
    }

    private long passageId(String track) {
        return jdbc.queryForObject("select passage_id from track where id = ?", Long.class, tracks.get(track));
    }

    private void heard(long student, String track, String coverage, boolean completed) {
        jdbc.update(
                "insert into track_progress (student_id, track_id, merged_ranges, covered_ms, coverage, completed_at,"
                        + " last_played_at) values (?, ?, '[]', 0, ?, ?, '2026-11-05 10:00:00')",
                student,
                tracks.get(track),
                new BigDecimal(coverage),
                completed ? "2026-11-05 10:00:00" : null);
    }

    /** 저장된 지문 집계. refTrack·refDurationMs가 지금의 기준 트랙·길이와 같아야 "쓸 수 있는 행"이다 */
    private void combined(long student, String refTrack, int refDurationMs, String coverage, boolean completed) {
        jdbc.update(
                "insert into passage_progress (student_id, passage_id, ref_track_id, ref_duration_ms, merged_ranges,"
                        + " covered_ms, coverage, completed_at) values (?, ?, ?, ?, '[]', 0, ?, ?)",
                student,
                passageId(refTrack),
                tracks.get(refTrack),
                refDurationMs,
                new BigDecimal(coverage),
                completed ? "2026-11-05 10:00:00" : null);
    }

    private void requiredVoices(String anyOfJson) {
        jdbc.update(
                "update completion_rule set required_tracks = ?"
                        + " where level_id = (select level_id from book where code = 'L2-B1')",
                "[{\"kind\":\"passage\",\"eachPassage\":true,\"anyOf\":" + anyOfJson + ",\"combine\":true}]");
    }

    private PassageProgressSummary passage(ListeningDetail detail, long student, String track) {
        return detail.passages().get(student).get(passageId(track));
    }

    @Test
    void 섞어_들어_완료한_지문은_완료다() {
        // 죽여야 하는 틀린 구현: 트랙 완료만 보는 것 — 두 목소리 모두 미완료라 미완료가 나온다
        heard(a, "p1:us_w", "0.5000", false);
        heard(a, "p1:us_m", "0.4500", false);
        combined(a, "p1:us_w", PASSAGE_MS, "0.9500", true);

        ListeningDetail detail = query.detailOf(students(a), List.of(content("L2-B1-05")));

        PassageSummaryAssert.of(passage(detail, a, "p1:us_w")).is("0.9500", true);
        assertThat(detail.tracks().get(a).get(tracks.get("p1:us_w")).completed())
                .isFalse();
        assertThat(detail.tracks().get(a).get(tracks.get("p1:us_m")).completed())
                .isFalse();
    }

    @Test
    void 저장_행이_없어도_필수_트랙을_완료했으면_완료다() {
        // 지문 집계 표가 생기기 전의 기록. 죽여야 하는 틀린 구현: 저장 행만 읽는 것(0·미완료가 나온다)
        heard(a, "p1:us_m", "0.9200", true);

        ListeningDetail detail = query.detailOf(students(a), List.of(content("L2-B1-05")));

        PassageSummaryAssert.of(passage(detail, a, "p1:us_w")).is("0.9200", true);
    }

    @Test
    void 쓸_수_없는_저장_행은_청취율에_쓰지_않고_완료는_유지한다() {
        // 기준 길이가 지금과 다른 행(음원이 바뀜). 죽여야 하는 틀린 구현: 저장 행의 청취율을 그대로 내보내는 것(0.9900이 나간다)
        heard(a, "p1:us_w", "0.4000", false);
        combined(a, "p1:us_w", PASSAGE_MS + 1000, "0.9900", true);

        ListeningDetail detail = query.detailOf(students(a), List.of(content("L2-B1-05")));

        PassageSummaryAssert.of(passage(detail, a, "p1:us_w")).is("0.4000", true);
    }

    @Test
    void 필수가_아닌_목소리만_들으면_0이고_미완료다() {
        // 죽여야 하는 틀린 구현: 필수 여부를 보지 않고 모든 목소리의 최댓값·완료를 쓰는 것
        requiredVoices("[\"us_w\"]");
        heard(a, "p1:us_m", "1.0000", true);

        ListeningDetail detail = query.detailOf(students(a), List.of(content("L2-B1-05")));

        PassageSummaryAssert.of(passage(detail, a, "p1:us_w")).is("0", false);
    }

    @Test
    void 필수_목소리_트랙이_없는_지문은_오류가_아니라_0이고_미완료다() {
        // 음원이 아직 없는 목소리만 필수인 규칙 — 기준 목소리가 없다
        requiredVoices("[\"uk_m\"]");
        heard(a, "p1:us_w", "1.0000", true);

        ListeningDetail detail = query.detailOf(students(a), List.of(content("L2-B1-05")));

        PassageSummaryAssert.of(passage(detail, a, "p1:us_w")).is("0", false);
        PassageSummaryAssert.of(passage(detail, a, "p2:us_w")).is("0", false);
    }

    @Test
    void 모든_학생과_모든_지문이_들어_있다() {
        heard(a, "p1:us_w", "0.3000", false);

        ListeningDetail detail = query.detailOf(students(a, b), List.of(content("L2-B1-05")));

        assertThat(detail.passages()).containsOnlyKeys(a, b);
        assertThat(detail.passages().get(a)).containsOnlyKeys(passageId("p1:us_w"), passageId("p2:us_w"));
        assertThat(detail.passages().get(b)).containsOnlyKeys(passageId("p1:us_w"), passageId("p2:us_w"));
        PassageSummaryAssert.of(passage(detail, a, "p1:us_w")).is("0.3000", false);
        PassageSummaryAssert.of(passage(detail, a, "p2:us_w")).is("0", false);
        PassageSummaryAssert.of(passage(detail, b, "p1:us_w")).is("0", false);
        assertThat(detail.tracks()).as("트랙 집계는 행이 있는 것만").containsOnlyKeys(a);
    }

    @Test
    void 다른_학생의_집계가_섞이지_않는다() {
        // 죽여야 하는 틀린 구현: 지문 집계를 학생 조건 없이 읽는 것(b의 완료가 a에 붙는다)
        heard(b, "p1:us_w", "0.5000", false);
        combined(b, "p1:us_w", PASSAGE_MS, "0.9500", true);

        ListeningDetail both = query.detailOf(students(a, b), List.of(content("L2-B1-05")));
        PassageSummaryAssert.of(passage(both, a, "p1:us_w")).is("0", false);
        PassageSummaryAssert.of(passage(both, b, "p1:us_w")).is("0.9500", true);

        ListeningDetail onlyA = query.detailOf(students(a, b).only(a), List.of(content("L2-B1-05")));
        assertThat(onlyA.passages()).containsOnlyKeys(a);
        assertThat(onlyA.tracks()).isEmpty();
    }

    @Test
    void 회차_여러_개를_한_번에_받는다() {
        Map<String, Long> other = data.lessonContent("L2-B1-04", 1, 2, List.of("us_w"));
        tracks = new java.util.LinkedHashMap<>(tracks);
        tracks.put("L4p1:us_w", other.get("p1:us_w"));
        heard(a, "p2:us_m", "0.9100", true);
        heard(a, "L4p1:us_w", "0.2000", false);
        List<LessonContent> lessons = List.of(content("L2-B1-04"), content("L2-B1-05"));

        ListeningDetail detail = query.detailOf(students(a), lessons);

        assertThat(detail.passages().get(a)).hasSize(3);
        PassageSummaryAssert.of(passage(detail, a, "p2:us_w")).is("0.9100", true);
        PassageSummaryAssert.of(passage(detail, a, "L4p1:us_w")).is("0.2000", false);
        PassageSummaryAssert.of(passage(detail, a, "p1:us_w")).is("0", false);
        assertThat(detail.tracks())
                .as("트랙 집계는 기존 조회와 같다")
                .isEqualTo(trackQuery.trackProgressOf(students(a), tracks.values()));
    }

    @Test
    void 빈_묶음과_빈_회차는_빈_결과다() {
        heard(a, "p1:us_w", "0.9200", true);

        ListeningDetail noStudents = query.detailOf(students(), List.of(content("L2-B1-05")));
        ListeningDetail noLessons = query.detailOf(students(a), List.of());

        assertThat(noStudents.tracks()).isEmpty();
        assertThat(noStudents.passages()).isEmpty();
        assertThat(noLessons.tracks()).isEmpty();
        assertThat(noLessons.passages()).isEmpty();
    }

    /** 청취율은 자릿수와 상관없이 값으로 비교한다(0.9500 == 0.95) */
    record PassageSummaryAssert(PassageProgressSummary actual) {
        static PassageSummaryAssert of(PassageProgressSummary actual) {
            assertThat(actual).isNotNull();
            return new PassageSummaryAssert(actual);
        }

        void is(String coverage, boolean completed) {
            assertThat(actual.coverage()).isEqualByComparingTo(coverage);
            assertThat(actual.completed()).isEqualTo(completed);
        }
    }
}
