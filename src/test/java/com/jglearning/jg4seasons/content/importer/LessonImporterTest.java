package com.jglearning.jg4seasons.content.importer;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.content.api.LessonCatalog;
import com.jglearning.jg4seasons.content.api.LessonContent;
import com.jglearning.jg4seasons.content.api.LessonContent.TimeRange;
import com.jglearning.jg4seasons.content.importer.ImportResult.Status;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** manifest로 회차 콘텐츠 넣기(ADR 0027 결정 8): 멱등, 청취 기록이 있는 트랙 보호. fixture 문장은 지어낸 것 */
@IntegrationTest
@Transactional
class LessonImporterTest {

    static final String CODE = "L1-B1-01";

    @Autowired
    LessonImporter importer;

    @Autowired
    LessonCatalog catalog;

    @Autowired
    JdbcTemplate jdbc;

    @TempDir
    Path dir;

    TestData data;

    @BeforeEach
    void setUp() {
        data = new TestData(jdbc);
    }

    @Test
    void 처음_넣으면_지문_문장_트랙_시각_단어가_생긴다() throws IOException {
        ImportResult result = importer.importLesson(manifest());

        assertThat(result).isEqualTo(new ImportResult(CODE, Status.IMPORTED, null));
        assertThat(jdbc.queryForMap("select title, passage_count from lesson where code = ?", CODE))
                .containsEntry("title", "시험용 회차")
                .containsEntry("passage_count", 2);
        assertThat(jdbc.queryForList(
                        "select variant from track where lesson_id = ? and kind = 'passage' order by sort_order",
                        String.class,
                        lessonId()))
                .containsExactly(
                        "p1:us_w", "p1:us_m", "p1:uk_w", "p1:uk_m", "p2:us_w", "p2:us_m", "p2:uk_w", "p2:uk_m");
        // 간격 (3800 - 3000) / 1 = 800
        assertThat(timings("p1:us_w")).containsExactly(new TimeRange(0, 1500), new TimeRange(2300, 3800));
        assertThat(timings("p2:uk_m")).containsExactly(new TimeRange(0, 1300));
        assertThat(jdbc.queryForList(
                        "select s.speaker from sentence s join passage p on p.id = s.passage_id"
                                + " where p.lesson_id = ? and p.no = 1 order by s.no",
                        String.class,
                        lessonId()))
                .containsExactly(null, "Mia");

        List<Map<String, Object>> words = jdbc.queryForList(
                "select w.sort_order, w.english, w.pos, w.passage_id, t.kind, t.variant, t.file_key, t.duration_ms,"
                        + " e.kind as ekind, e.variant as evariant, e.file_key as ekey"
                        + " from word w join track t on t.id = w.track_id join track e on e.id = w.example_track_id"
                        + " where w.lesson_id = ? order by w.sort_order",
                lessonId());
        assertThat(words).hasSize(2);
        assertThat(words.get(1))
                .containsEntry("english", "all day")
                .containsEntry("pos", "phr")
                .containsEntry("passage_id", null)
                .containsEntry("kind", "word")
                .containsEntry("variant", "w02:us_w")
                .containsEntry("file_key", "audio/L1-B1-01/L1-B1-01_w02_us_w.mp3")
                .containsEntry("duration_ms", 800)
                .containsEntry("ekind", "word_example")
                .containsEntry("evariant", "w02:us_w")
                .containsEntry("ekey", "audio/L1-B1-01/L1-B1-01_w02_ex_us_w.mp3");
    }

    @Test
    void 같은_manifest를_다시_넣으면_UNCHANGED이고_행_id가_그대로다() throws IOException {
        importer.importLesson(manifest());
        List<Long> before = rowIds();

        ImportResult again = importer.importLesson(manifest());

        assertThat(again.status()).isEqualTo(Status.UNCHANGED);
        assertThat(rowIds()).containsExactlyInAnyOrderElementsOf(before);
    }

    @Test
    void 기록이_있는_트랙_옆_다른_목소리와_단어_음원의_길이_변경은_고친다() throws IOException {
        importer.importLesson(manifest());
        listen("p1:us_w");
        List<Long> before = rowIds();

        // p1:us_m 3900 → 4000(간격 900), 단어 1 음원 700 → 750
        ImportResult result = importer.importLesson(
                manifest("\"durationMs\": 3900", "\"durationMs\": 4000", "\"durationMs\": 700", "\"durationMs\": 750"));

        assertThat(result.status()).isEqualTo(Status.IMPORTED);
        assertThat(duration("p1:us_m")).isEqualTo(4000);
        assertThat(timings("p1:us_m")).containsExactly(new TimeRange(0, 1550), new TimeRange(2450, 4000));
        assertThat(jdbc.queryForObject(
                        "select duration_ms from track where lesson_id = ? and kind = 'word' and variant = 'w01:us_w'",
                        Integer.class,
                        lessonId()))
                .isEqualTo(750);
        assertThat(rowIds()).containsExactlyInAnyOrderElementsOf(before);
    }

    @Test
    void 원고를_고치면_문장_화자_단어_뜻이_바뀌고_행_id는_그대로다() throws IOException {
        importer.importLesson(manifest());
        List<Long> before = rowIds();

        ImportResult result = importer.importLesson(manifest(
                // 문장 1은 글만, 문장 2는 화자만 바꾼다(둘 중 하나의 비교가 빠지면 잡히게)
                "\"text\": \"Mia flies a red kite.\"",
                "\"text\": \"Mia flies a blue kite.\"",
                "\"speaker\": \"Mia\"",
                "\"speaker\": \"Leo\"",
                "\"korean\": \"연\"",
                "\"korean\": \"연(놀이)\""));

        assertThat(result.status()).isEqualTo(Status.IMPORTED);
        List<Map<String, Object>> sentences = jdbc.queryForList(
                "select s.text, s.speaker from sentence s join passage p on p.id = s.passage_id"
                        + " where p.lesson_id = ? and p.no = 1 order by s.no",
                lessonId());
        assertThat(sentences.get(0))
                .containsEntry("text", "Mia flies a blue kite.")
                .containsEntry("speaker", null);
        // 굽은 따옴표가 utf8mb4로 그대로
        assertThat(sentences.get(1))
                .containsEntry("text", "“It’s so high!” she says.")
                .containsEntry("speaker", "Leo");
        assertThat(jdbc.queryForObject(
                        "select korean from word where lesson_id = ? and sort_order = 1", String.class, lessonId()))
                .isEqualTo("연(놀이)");
        assertThat(rowIds()).containsExactlyInAnyOrderElementsOf(before);
    }

    @Test
    void 청취_기록이_있는_트랙의_길이가_바뀌면_회차_전체를_거부한다() throws IOException {
        importer.importLesson(manifest());
        listen("p1:us_w");

        ImportResult result = importer.importLesson(
                manifest("\"durationMs\": 3800", "\"durationMs\": 3900", "\"The Red Kite\"", "\"The Blue Kite\""));

        assertThat(result.status()).isEqualTo(Status.REJECTED);
        assertThat(result.reason()).contains("p1:us_w");
        assertThat(passageTitle(1)).isEqualTo("The Red Kite");
        assertThat(duration("p1:us_w")).isEqualTo(3800);
    }

    @Test
    void 청취_기록이_있어도_길이가_같으면_문장_시각과_제목은_고친다() throws IOException {
        importer.importLesson(manifest());
        listen("p1:us_w");

        ImportResult result = importer.importLesson(manifest(
                "\"sentenceMs\": [1500, 1500]",
                "\"sentenceMs\": [1400, 1600]",
                "\"The Red Kite\"",
                "\"The Blue Kite\""));

        assertThat(result.status()).isEqualTo(Status.IMPORTED);
        assertThat(passageTitle(1)).isEqualTo("The Blue Kite");
        assertThat(timings("p1:us_w")).containsExactly(new TimeRange(0, 1400), new TimeRange(2200, 3800));
    }

    @Test
    void 지문이_줄어_기록_있는_트랙이_사라지면_거부한다() throws IOException {
        importer.importLesson(manifest());
        listen("p2:uk_m");
        LessonManifest full = manifest();
        LessonManifest shorter =
                new LessonManifest(full.code(), full.title(), full.passages().subList(0, 1), full.words());

        ImportResult result = importer.importLesson(shorter);

        assertThat(result.status()).isEqualTo(Status.REJECTED);
        assertThat(result.reason()).contains("p2:uk_m");
        assertThat(jdbc.queryForObject("select count(*) from passage where lesson_id = ?", Integer.class, lessonId()))
                .isEqualTo(2);
    }

    @Test
    void 기록이_없으면_줄어든_지문과_그_트랙_시각_문장을_지운다() throws IOException {
        importer.importLesson(manifest());
        LessonManifest full = manifest();

        ImportResult result = importer.importLesson(new LessonManifest(
                full.code(),
                full.title(),
                full.passages().subList(0, 1),
                full.words().subList(0, 1)));

        assertThat(result.status()).isEqualTo(Status.IMPORTED);
        assertThat(jdbc.queryForList("select no from passage where lesson_id = ?", Integer.class, lessonId()))
                .containsExactly(1);
        assertThat(jdbc.queryForObject("select count(*) from track where lesson_id = ?", Integer.class, lessonId()))
                .isEqualTo(4 + 2);
        assertThat(jdbc.queryForObject("select count(*) from word where lesson_id = ?", Integer.class, lessonId()))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select passage_count from lesson where id = ?", Integer.class, lessonId()))
                .isEqualTo(1);
    }

    @Test
    void 개발_시드_모양의_내용을_덮어쓴다() throws IOException {
        // 지문 3 × 문장 3 × 목소리 4, 단어 3개(지문 1에 붙음)
        data.lessonContent(CODE, 3, 3, List.of("us_w", "us_m", "uk_w", "uk_m"));
        data.words(CODE, 3);

        ImportResult result = importer.importLesson(manifest());

        assertThat(result.status()).isEqualTo(Status.IMPORTED);
        assertThat(jdbc.queryForList(
                        "select no from passage where lesson_id = ? order by no", Integer.class, lessonId()))
                .containsExactly(1, 2);
        assertThat(jdbc.queryForObject(
                        "select count(*) from sentence s join passage p on p.id = s.passage_id where p.lesson_id = ?",
                        Integer.class,
                        lessonId()))
                .isEqualTo(3);
        assertThat(jdbc.queryForList(
                        "select english from word where lesson_id = ? and passage_id is null order by sort_order",
                        String.class,
                        lessonId()))
                .containsExactly("kite", "all day");
        assertThat(timings("p1:us_w")).containsExactly(new TimeRange(0, 1500), new TimeRange(2300, 3800));
    }

    @Test
    void 기준_데이터에_없는_회차는_거부한다() throws IOException {
        String text = fixture().replace(CODE, "K-B4-01");
        Path file = dir.resolve("K-B4-01.json");
        Files.writeString(file, text, StandardCharsets.UTF_8);

        ImportResult result = importer.importLesson(new ManifestReader().read(file));

        assertThat(result.status()).isEqualTo(Status.REJECTED);
        assertThat(result.reason()).contains("기준 데이터");
    }

    @Test
    void 문장_시각을_만들_수_없으면_아무것도_넣지_않고_거부한다() throws IOException {
        // 간격 (9000 - 3000) / 1 = 6000ms > 3000ms
        ImportResult result = importer.importLesson(manifest("\"durationMs\": 3800", "\"durationMs\": 9000"));

        assertThat(result.status()).isEqualTo(Status.REJECTED);
        assertThat(result.reason()).contains("지문 1").contains("us_w");
        assertThat(jdbc.queryForObject("select count(*) from passage where lesson_id = ?", Integer.class, lessonId()))
                .isZero();
    }

    @Test
    void 넣은_회차를_학생_회차_상세가_목소리_4개로_보여준다() throws IOException {
        importer.importLesson(manifest());

        LessonContent lesson = catalog.findByCode(CODE).orElseThrow();

        assertThat(lesson.title()).isEqualTo("시험용 회차");
        assertThat(lesson.wordTotal()).isEqualTo(2);
        LessonContent.Passage first = lesson.passages().getFirst();
        assertThat(first.voices())
                .extracting(LessonContent.Voice::voice)
                .containsExactly("us_w", "us_m", "uk_w", "uk_m");
        assertThat(first.voices().get(2).timings()).containsExactly(new TimeRange(0, 1450), new TimeRange(2250, 3700));
        assertThat(first.voices().get(2).timingsApproximate()).isFalse();
    }

    private long lessonId() {
        return data.lessonId(CODE);
    }

    private List<TimeRange> timings(String variant) {
        return jdbc.query(
                "select st.start_ms, st.end_ms from sentence_timing st join track t on t.id = st.track_id"
                        + " join sentence s on s.id = st.sentence_id"
                        + " where t.lesson_id = ? and t.variant = ? and t.kind = 'passage' order by s.no",
                (rs, i) -> new TimeRange(rs.getInt("start_ms"), rs.getInt("end_ms")),
                lessonId(),
                variant);
    }

    private long trackId(String variant) {
        return jdbc.queryForObject(
                "select id from track where lesson_id = ? and kind = 'passage' and variant = ?",
                Long.class,
                lessonId(),
                variant);
    }

    private int duration(String variant) {
        return jdbc.queryForObject("select duration_ms from track where id = ?", Integer.class, trackId(variant));
    }

    private String passageTitle(int no) {
        return jdbc.queryForObject(
                "select title from passage where lesson_id = ? and no = ?", String.class, lessonId(), no);
    }

    /** 학생 한 명이 그 지문 트랙을 듣기 시작했다 */
    private void listen(String variant) {
        long student = data.student(data.academy("시험 학원"), "학생");
        LocalDateTime now = LocalDateTime.of(2026, 11, 10, 9, 0);
        jdbc.update(
                "insert into listening_session (client_session_id, student_id, track_id, started_at, last_seen_at)"
                        + " values (?, ?, ?, ?, ?)",
                UUID.randomUUID().toString(),
                student,
                trackId(variant),
                now,
                now);
    }

    /** 회차의 지문·문장·트랙·단어 행 id 전부 */
    private List<Long> rowIds() {
        return jdbc.queryForList(
                "select id from passage where lesson_id = ?"
                        + " union all select s.id from sentence s join passage p on p.id = s.passage_id where p.lesson_id = ?"
                        + " union all select id from track where lesson_id = ?"
                        + " union all select id from word where lesson_id = ?",
                Long.class,
                lessonId(),
                lessonId(),
                lessonId(),
                lessonId());
    }

    /** fixture를 from → to 쌍으로 바꿔 읽는다 */
    private LessonManifest manifest(String... replacements) throws IOException {
        String text = fixture();
        for (int i = 0; i < replacements.length; i += 2) {
            assertThat(text).contains(replacements[i]);
            text = text.replace(replacements[i], replacements[i + 1]);
        }
        Path file = dir.resolve(CODE + ".json");
        Files.writeString(file, text, StandardCharsets.UTF_8);
        return new ManifestReader().read(file);
    }

    private static String fixture() throws IOException {
        try (var in = LessonImporterTest.class.getResourceAsStream("/content-import/L1-B1-01.json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
