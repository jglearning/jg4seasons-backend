package com.jglearning.jg4seasons.content.importer;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.content.importer.ImportResult.Status;
import com.jglearning.jg4seasons.content.importer.LessonManifest.Word;
import com.jglearning.jg4seasons.content.importer.LessonManifest.WordTestItem;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * manifest의 단어시험 문항(wordTests) 넣기: 칸이 없으면 그대로, 있으면 그 회차 전체 목록. 답안이 있는 문항의 삭제·정답 변경은 회차 전체 거부. 단어 연결(word_id)은 import마다 같은
 * 철자 단어가 회차에 정확히 하나일 때만. 픽스처 단어는 kite·all day·everyone·puddle, 문항은 지어낸 것
 */
@IntegrationTest
@Transactional
class WordTestImportTest {

    static final String CODE = "L1-B1-01";

    static final WordTestItem KITE = new WordTestItem("B", 1, "연", "kite", null, List.of());
    static final WordTestItem ALL_DAY = new WordTestItem("B", 2, "하루 종일", "all day", "a... d...", List.of());
    static final WordTestItem RAINBOW = new WordTestItem("B", 3, "무지개", "rainbow", "r", List.of("rain bow"));
    static final WordTestItem PUDDLE = new WordTestItem("B", 4, "웅덩이", "puddle", null, List.of());
    static final WordTestItem PUDDLE_2 = new WordTestItem("B", 2, "웅덩이", "puddle", null, List.of());
    static final WordTestItem MEANING = new WordTestItem("A", 1, "kite", "연", null, List.of());

    @Autowired
    LessonImporter importer;

    @Autowired
    JdbcTemplate jdbc;

    @TempDir
    Path dir;

    TestData data;
    LessonManifest base;

    @BeforeEach
    void setUp() throws IOException {
        data = new TestData(jdbc);
        base = fixture();
    }

    @Test
    void 처음_넣으면_문항과_허용_답이_생기고_같은_철자의_단어에_연결한다() {
        ImportResult result = importer.importLesson(with(KITE, ALL_DAY, RAINBOW, PUDDLE, MEANING));

        assertThat(result).isEqualTo(new ImportResult(CODE, Status.IMPORTED, null));
        assertThat(items())
                .containsExactly(
                        "A 1 kite 연 - -",
                        "B 1 연 kite - kite",
                        "B 2 하루 종일 all day a... d... all day",
                        "B 3 무지개 rainbow r -",
                        "B 4 웅덩이 puddle - puddle");
        assertThat(jdbc.queryForList(
                        "select a.text from word_test_accepted_answer a join word_test_item i on i.id = a.item_id"
                                + " where i.lesson_id = ?",
                        String.class,
                        lessonId()))
                .containsExactly("rain bow");
    }

    @Test
    void 같은_manifest를_다시_넣으면_UNCHANGED이고_문항_id가_그대로다() {
        importer.importLesson(with(KITE, ALL_DAY, RAINBOW));
        List<Long> before = itemIds();

        ImportResult again = importer.importLesson(with(KITE, ALL_DAY, RAINBOW));

        assertThat(again.status()).isEqualTo(Status.UNCHANGED);
        assertThat(itemIds()).isEqualTo(before);
    }

    @Test
    void wordTests가_없는_manifest는_문항을_그대로_둔다() {
        importer.importLesson(with(KITE, ALL_DAY, RAINBOW));

        ImportResult result = importer.importLesson(base);

        assertThat(result.status()).isEqualTo(Status.UNCHANGED);
        assertThat(items()).hasSize(3);
    }

    @Test
    void 목록에서_빠진_문항과_영역은_지운다() {
        importer.importLesson(with(KITE, ALL_DAY, RAINBOW, MEANING));

        ImportResult result = importer.importLesson(with(KITE, ALL_DAY));

        assertThat(result.status()).isEqualTo(Status.IMPORTED);
        assertThat(items()).containsExactly("B 1 연 kite - kite", "B 2 하루 종일 all day a... d... all day");
        assertThat(jdbc.queryForObject("select count(*) from word_test_accepted_answer", Integer.class))
                .isZero();
    }

    @Test
    void 답안이_있는_문항의_정답이_바뀌면_회차_전체를_거부한다() {
        importer.importLesson(with(KITE, ALL_DAY));
        answered("B", 1);
        List<Word> renamed = new ArrayList<>(base.words());
        Word puddle = renamed.get(3);
        renamed.set(3, copy(puddle, "puddles"));

        ImportResult result = importer.importLesson(new LessonManifest(
                CODE,
                base.passages(),
                renamed,
                List.of(new WordTestItem("B", 1, "연", "kites", null, List.of()), ALL_DAY)));

        assertThat(result.status()).isEqualTo(Status.REJECTED);
        assertThat(result.reason()).contains("답안이 있는 단어시험 문항").contains("B 1 정답 변경");
        assertThat(items()).contains("B 1 연 kite - kite");
        assertThat(jdbc.queryForObject(
                        "select english from word where lesson_id = ? and sort_order = 4", String.class, lessonId()))
                .as("같은 회차의 다른 변경도 넣지 않는다")
                .isEqualTo("puddle");
    }

    @Test
    void 답안이_있는_문항을_지우면_거부한다() {
        importer.importLesson(with(KITE, ALL_DAY));
        answered("B", 2);

        ImportResult result = importer.importLesson(with(KITE));

        assertThat(result.status()).isEqualTo(Status.REJECTED);
        assertThat(result.reason()).contains("B 2 삭제");
        assertThat(items()).hasSize(2);
    }

    @Test
    void 답안이_있어도_뜻_hint_허용_답은_고친다() {
        importer.importLesson(with(KITE, ALL_DAY));
        answered("B", 1);

        ImportResult result =
                importer.importLesson(with(new WordTestItem("B", 1, "연, 솔개", "kite", "k", List.of("kyte")), ALL_DAY));

        assertThat(result.status()).isEqualTo(Status.IMPORTED);
        assertThat(items()).contains("B 1 연, 솔개 kite k kite");
        assertThat(jdbc.queryForList("select text from word_test_accepted_answer", String.class))
                .containsExactly("kyte");
    }

    @Test
    void 단어가_지워지면_문항_연결을_끊는다() {
        importer.importLesson(with(KITE, PUDDLE_2));

        ImportResult result = importer.importLesson(
                new LessonManifest(CODE, base.passages(), base.words().subList(0, 3), List.of(KITE, PUDDLE_2)));

        assertThat(result.status()).isEqualTo(Status.IMPORTED);
        assertThat(items()).containsExactly("B 1 연 kite - kite", "B 2 웅덩이 puddle - -");
    }

    @Test
    void 단어_철자가_바뀌면_연결을_다시_계산한다() {
        importer.importLesson(with(KITE, PUDDLE_2));
        List<Word> renamed = new ArrayList<>(base.words());
        renamed.set(0, copy(renamed.get(0), "kites"));

        importer.importLesson(new LessonManifest(CODE, base.passages(), renamed, List.of(KITE, PUDDLE_2)));

        assertThat(items()).containsExactly("B 1 연 kite - -", "B 2 웅덩이 puddle - puddle");
    }

    @Test
    void 회차에_같은_철자_단어가_둘이면_연결하지_않는다() {
        List<Word> twice = new ArrayList<>(base.words());
        twice.set(2, copy(twice.get(2), "kite"));

        importer.importLesson(new LessonManifest(CODE, base.passages(), twice, List.of(KITE)));

        assertThat(items()).containsExactly("B 1 연 kite - -");
    }

    @Test
    void 철자는_대소문자까지_같아야_연결한다() {
        importer.importLesson(with(new WordTestItem("B", 1, "연", "Kite", null, List.of())));

        assertThat(items()).containsExactly("B 1 연 Kite - -");
    }

    @Test
    void wordTests가_없는_manifest로_단어를_지워도_문항_연결만_끊는다() {
        importer.importLesson(with(KITE, PUDDLE_2));

        ImportResult result = importer.importLesson(
                new LessonManifest(CODE, base.passages(), base.words().subList(0, 3), null));

        assertThat(result.status()).isEqualTo(Status.IMPORTED);
        assertThat(items()).containsExactly("B 1 연 kite - kite", "B 2 웅덩이 puddle - -");
    }

    @Test
    void wordTests가_없는_manifest로_철자가_바뀌어도_연결을_다시_계산한다() {
        importer.importLesson(with(KITE, PUDDLE_2));
        List<Word> renamed = new ArrayList<>(base.words());
        renamed.set(0, copy(renamed.get(0), "kites"));

        importer.importLesson(new LessonManifest(CODE, base.passages(), renamed, null));

        assertThat(items()).containsExactly("B 1 연 kite - -", "B 2 웅덩이 puddle - puddle");
    }

    @Test
    void 답안이_없는_문항은_정답을_고치고_연결도_옮긴다() {
        importer.importLesson(with(KITE));
        List<Long> before = itemIds();

        ImportResult result = importer.importLesson(with(new WordTestItem("B", 1, "웅덩이", "puddle", null, List.of())));

        assertThat(result.status()).isEqualTo(Status.IMPORTED);
        assertThat(items()).containsExactly("B 1 웅덩이 puddle - puddle");
        assertThat(itemIds()).isEqualTo(before);
    }

    @Test
    void 빈_목록이면_문항을_모두_지운다() {
        importer.importLesson(with(KITE, ALL_DAY, RAINBOW));

        ImportResult result = importer.importLesson(with());

        assertThat(result.status()).isEqualTo(Status.IMPORTED);
        assertThat(items()).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from word_test_accepted_answer", Integer.class))
                .isZero();
    }

    private LessonManifest with(WordTestItem... items) {
        return new LessonManifest(CODE, base.passages(), base.words(), List.of(items));
    }

    private static Word copy(Word word, String english) {
        return new Word(
                word.no(),
                english,
                word.source(),
                word.pos(),
                word.korean(),
                word.exampleEn(),
                word.exampleKo(),
                word.track(),
                word.exampleTrack());
    }

    /** "영역 번호 뜻 정답 hint 연결된_단어" — 없으면 - */
    private List<String> items() {
        return jdbc.query(
                "select i.section, i.no, i.prompt, i.answer, i.hint, w.english from word_test_item i"
                        + " left join word w on w.id = i.word_id where i.lesson_id = ? order by i.section, i.no",
                (rs, n) -> String.join(
                        " ",
                        rs.getString("section"),
                        String.valueOf(rs.getInt("no")),
                        rs.getString("prompt"),
                        rs.getString("answer"),
                        dash(rs.getString("hint")),
                        dash(rs.getString("english"))),
                lessonId());
    }

    private static String dash(String value) {
        return value == null ? "-" : value;
    }

    private List<Long> itemIds() {
        return jdbc.queryForList(
                "select id from word_test_item where lesson_id = ? order by section, no", Long.class, lessonId());
    }

    /** 학생 한 명이 그 문항에 답안을 냈다 */
    private void answered(String section, int no) {
        long student = data.student(data.academy("시험 학원"), "학생");
        jdbc.update(
                "insert into word_test_submission (client_submission_id, student_id, lesson_id, section, attempt_no,"
                        + " status, total) values (?, ?, ?, ?, 1, 'graded', 2)",
                UUID.randomUUID().toString(),
                student,
                lessonId(),
                section);
        long submission = jdbc.queryForObject("select last_insert_id()", Long.class);
        Map<String, Object> item = jdbc.queryForMap(
                "select id from word_test_item where lesson_id = ? and section = ? and no = ?",
                lessonId(),
                section,
                no);
        jdbc.update(
                "insert into word_test_answer (submission_id, item_id, recognized_text, result)"
                        + " values (?, ?, 'kite', 'correct')",
                submission,
                item.get("id"));
    }

    private long lessonId() {
        return data.lessonId(CODE);
    }

    private LessonManifest fixture() throws IOException {
        try (var in = WordTestImportTest.class.getResourceAsStream("/content-import/" + CODE + ".json")) {
            Path file = dir.resolve(CODE + ".json");
            Files.write(file, in.readAllBytes());
            return new ManifestReader().read(file);
        }
    }
}
