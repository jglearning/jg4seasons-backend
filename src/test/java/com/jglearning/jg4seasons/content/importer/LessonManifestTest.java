package com.jglearning.jg4seasons.content.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LessonManifestTest {

    private static final String FIXTURE = "/content-import/L1-B1-01.json";

    @TempDir
    Path dir;

    private final ManifestReader reader = new ManifestReader();

    @Test
    void 정상_manifest를_읽는다() throws IOException {
        LessonManifest manifest = readWith(null, null);

        assertThat(manifest.code()).isEqualTo("L1-B1-01");
        assertThat(manifest.passages()).hasSize(2);
        LessonManifest.Passage first = manifest.passages().getFirst();
        assertThat(first.tracks())
                .extracting(LessonManifest.Track::voice)
                .containsExactly("us_w", "us_m", "uk_w", "uk_m");
        assertThat(first.sentences().get(0).speaker()).isNull();
        assertThat(first.sentences().get(1).text()).isEqualTo("“It’s so high!” she says.");
        assertThat(manifest.words()).extracting(LessonManifest.Word::pos).containsExactly("n", "phr", "pron", "n");
        // 출처(ADR 0029): 어휘책 단어(녹음 자료에도 있는 것 1·2, 어휘책에만 있는 것 3) → 어휘책에 없는 회차 어휘 4
        assertThat(manifest.words())
                .extracting(LessonManifest.Word::source)
                .containsExactly("book", "book", "book", "extra");
        assertThat(manifest.words().get(1).exampleTrack().fileKey())
                .isEqualTo("audio/L1-B1-01/L1-B1-01_w02_ex_us_w.mp3");
        assertThat(manifest.words().get(1).audioNo()).isEqualTo(2);
        // 어휘책에만 있는 단어: 음원·예문 해석 없음(ADR 0027 결정 6)
        LessonManifest.Word bookOnly = manifest.words().get(2);
        assertThat(bookOnly.track()).isNull();
        assertThat(bookOnly.exampleTrack()).isNull();
        assertThat(bookOnly.exampleKo()).isNull();
        assertThat(bookOnly.audioNo()).isNull();
    }

    @Test
    void 음원_번호는_단어_순서와_달라도_된다() throws IOException {
        LessonManifest manifest = readWith("_w02_", "_w07_");

        assertThat(manifest.words().get(1).no()).isEqualTo(2);
        assertThat(manifest.words().get(1).audioNo()).isEqualTo(7);
    }

    @Test
    void 음원_번호가_단어끼리_겹치면_거부() {
        assertRejected("_w02_", "_w01_", "단어 2");
    }

    @Test
    void 예문_음원만_있으면_거부() {
        assertRejected(
                "\"track\": { \"fileKey\": \"audio/L1-B1-01/L1-B1-01_w02_us_w.mp3\", \"durationMs\": 800 }",
                "\"track\": null",
                "단어 2");
    }

    @Test
    void 추가_어휘는_예문_음원도_있어야_한다() {
        // 단어 음원만 허용한 것은 새 어휘책 판(book) 때문이다. 회차 어휘(extra)는 녹음 자료에서 와서 예문 음원이 늘 있다
        assertRejected(
                "\"exampleTrack\": { \"fileKey\": \"audio/L1-B1-01/L1-B1-01_w05_ex_us_w.mp3\", \"durationMs\": 2000 }",
                "\"exampleTrack\": null",
                "단어 4");
    }

    @Test
    void 단어_음원만_있고_예문_음원이_없어도_읽는다() throws IOException {
        // 새 어휘책 판은 단어만 녹음했다(이슈 #88)
        LessonManifest manifest = readWith(
                "\"exampleTrack\": { \"fileKey\": \"audio/L1-B1-01/L1-B1-01_w02_ex_us_w.mp3\", \"durationMs\": 1900 }",
                "\"exampleTrack\": null");

        LessonManifest.Word word = manifest.words().get(1);
        assertThat(word.track().fileKey()).isEqualTo("audio/L1-B1-01/L1-B1-01_w02_us_w.mp3");
        assertThat(word.exampleTrack()).isNull();
        assertThat(word.audioNo()).isEqualTo(2);
    }

    @Test
    void 모르는_칸이_있으면_거부() {
        assertRejected("\"wordCount\": 11,", "\"wordCount\": 11, \"body\": \"x\",", "L1-B1-01.json");
    }

    @Test
    void 빠진_칸은_거부() {
        assertRejected("\"title\": \"Rainy Day\",", "", "L1-B1-01.json");
    }

    @Test
    void 파일_이름과_회차_코드가_다르면_거부() throws IOException {
        Path file = dir.resolve("L1-B1-02.json");
        Files.writeString(file, fixture(), StandardCharsets.UTF_8);

        assertThatThrownBy(() -> reader.read(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("L1-B1-02.json");
    }

    @Test
    void 회차_코드_형식이_다르면_거부() throws IOException {
        // 블랙 자료의 L5- 코드는 스크립트가 K-로 바꿔야 한다
        String text = fixture().replace("L1-B1-01", "L5-B1-01");
        Path file = dir.resolve("L5-B1-01.json");
        Files.writeString(file, text, StandardCharsets.UTF_8);

        assertThatThrownBy(() -> reader.read(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("L5-B1-01");
    }

    @Test
    void 블랙_보충_회차_코드를_받는다() throws IOException {
        String text = fixture().replace("L1-B1-01", "K-B1-S1");
        Path file = dir.resolve("K-B1-S1.json");
        Files.writeString(file, text, StandardCharsets.UTF_8);

        assertThat(reader.read(file).code()).isEqualTo("K-B1-S1");
    }

    @Test
    void 문장_수와_문장_길이_수가_다르면_거부() {
        assertRejected("\"sentenceMs\": [1550, 1550]", "\"sentenceMs\": [1550]", "지문 1");
    }

    @Test
    void 목소리가_4개가_아니면_거부() {
        assertRejected(
                "{ \"voice\": \"uk_m\", \"fileKey\": \"audio/L1-B1-01/L1-B1-01_p2_uk_m.mp3\", \"durationMs\": 1300,"
                        + " \"sentenceMs\": [1300] }",
                "{ \"voice\": \"uk_w\", \"fileKey\": \"audio/L1-B1-01/L1-B1-01_p2_uk_w.mp3\", \"durationMs\": 1300,"
                        + " \"sentenceMs\": [1300] }",
                "지문 2");
    }

    @Test
    void 지문_번호가_1부터_이어지지_않으면_거부() {
        assertRejected(
                "\"no\": 2,\n      \"title\": \"Rainy Day\"", "\"no\": 3,\n      \"title\": \"Rainy Day\"", "지문");
    }

    @Test
    void 문장_번호가_1부터_이어지지_않으면_거부() {
        assertRejected("{ \"no\": 2, \"text\"", "{ \"no\": 3, \"text\"", "지문 1");
    }

    @Test
    void 단어_번호가_1부터_이어지지_않으면_거부() {
        assertRejected("\"no\": 2,\n      \"english\"", "\"no\": 5,\n      \"english\"", "단어");
    }

    @Test
    void 지문_파일_키가_규칙과_다르면_거부() {
        assertRejected("audio/L1-B1-01/L1-B1-01_p1_us_m.mp3", "audio/L1-B1-02/L1-B1-02_p1_us_m.mp3", "지문 1");
    }

    @Test
    void 단어_파일_키가_규칙과_다르면_거부() {
        assertRejected("L1-B1-01_w02_ex_us_w.mp3", "L1-B1-01_w01_ex_us_w.mp3", "단어 2");
    }

    @Test
    void 단어_출처가_없거나_모르는_값이면_거부() {
        // 옛 manifest(출처 없음)가 조용히 book으로 들어가지 않게
        assertRejected("\"source\": \"extra\",\n", "", "단어 4");
        assertRejected("\"source\": \"extra\"", "\"source\": null", "단어 4");
        assertRejected("\"source\": \"extra\"", "\"source\": \"lesson\"", "단어 4");
    }

    @Test
    void 추가_어휘는_음원과_예문_해석이_있어야_한다() {
        // 회차 어휘(녹음 자료)에만 있는 단어라 늘 있다 — 없으면 스크립트가 출처를 잘못 붙인 것(리뷰 3)
        assertRejected("\"exampleKo\": \"그는 웅덩이에 뛰어들었다.\"", "\"exampleKo\": null", "단어 4");
        assertRejected(
                "      \"track\": { \"fileKey\": \"audio/L1-B1-01/L1-B1-01_w05_us_w.mp3\", \"durationMs\": 750 },\n"
                        + "      \"exampleTrack\": { \"fileKey\": \"audio/L1-B1-01/L1-B1-01_w05_ex_us_w.mp3\", \"durationMs\": 2000 }",
                "      \"track\": null,\n      \"exampleTrack\": null",
                "단어 4");
    }

    @Test
    void 추가_어휘_뒤에_어휘책_단어가_오면_거부() {
        // 교재 순서는 어휘책 단어·숙어 → 어휘책에 없는 회차 어휘(ADR 0027 결정 6)
        assertRejected(
                "\"english\": \"kite\",\n      \"source\": \"book\"",
                "\"english\": \"kite\",\n      \"source\": \"extra\"",
                "단어 2");
    }

    @Test
    void 단어가_127개를_넘으면_거부() throws IOException {
        // word.sort_order·word_study 칸이 TINYINT — DB 오류 대신 manifest에서 알린다(리뷰 10)
        StringBuilder extra = new StringBuilder();
        for (int no = 5; no <= 128; no++) {
            extra.append(",\n    { \"no\": ")
                    .append(no)
                    .append(", \"english\": \"w")
                    .append(no)
                    .append("\", \"source\": \"book\", \"pos\": \"n\", \"korean\": \"뜻\", \"exampleEn\": \"A line.\",")
                    .append(" \"exampleKo\": null, \"track\": null, \"exampleTrack\": null }");
        }
        String last = "\"durationMs\": 2000 }\n    }";
        String text = fixture();
        assertThat(text).contains(last);
        // 개수 검사가 순서 검사(추가 어휘 뒤 어휘책 단어)보다 먼저라 뒤에 붙여도 개수로 거부된다
        Path file = dir.resolve("L1-B1-01.json");
        Files.writeString(file, text.replace(last, last + extra), StandardCharsets.UTF_8);

        assertThatThrownBy(() -> reader.read(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("127");
    }

    @Test
    void 모르는_품사는_거부() {
        assertRejected("\"pos\": \"phr\"", "\"pos\": \"n.\"", "단어 2");
    }

    @Test
    void 문장은_800자까지() throws IOException {
        String max = "a".repeat(799) + "😀"; // MySQL은 문자 수로 센다(이모지 하나 = 1자)
        assertThat(readWith("It rains all day.", max)
                        .passages()
                        .get(1)
                        .sentences()
                        .getFirst()
                        .text())
                .isEqualTo(max);
        assertRejected("It rains all day.", "a".repeat(801), "지문 2");
    }

    @Test
    void 칸_길이가_한도를_넘으면_거부() {
        assertRejected("\"The Red Kite\"", "\"" + "a".repeat(121) + "\"", "지문 1");
        assertRejected("\"speaker\": \"Mia\"", "\"speaker\": \"" + "a".repeat(21) + "\"", "지문 1");
        assertRejected("\"english\": \"kite\"", "\"english\": \"" + "a".repeat(51) + "\"", "단어 1");
        assertRejected("\"korean\": \"연\"", "\"korean\": \"" + "가".repeat(101) + "\"", "단어 1");
        assertRejected(
                "\"exampleEn\": \"The kite is stuck in the tree.\"",
                "\"exampleEn\": \"" + "a".repeat(301) + "\"",
                "단어 1");
        assertRejected("\"exampleKo\": \"연이 나무에 걸렸다.\"", "\"exampleKo\": \"" + "가".repeat(301) + "\"", "단어 1");
    }

    @Test
    void 빈_문장은_거부() {
        assertRejected("It rains all day.", " ", "지문 2");
    }

    @Test
    void 길이가_0인_음원은_거부() {
        assertRejected("\"durationMs\": 700", "\"durationMs\": 0", "단어 1");
    }

    // 단어시험 문항(wordTests). 칸이 없으면 null — 그 회차 문항을 건드리지 않는다

    private static final String WORDS = "\"words\": [";

    private static String withWordTests(String items) {
        return "\"wordTests\": [" + items + "],\n  " + WORDS;
    }

    private static String item(String section, int no, String answer) {
        return "{\"section\": \"" + section + "\", \"no\": " + no + ", \"prompt\": \"뜻\", \"answer\": \"" + answer
                + "\"}";
    }

    @Test
    void 단어시험_문항이_없으면_null이다() throws IOException {
        assertThat(readWith(null, null).wordTests()).isNull();
    }

    @Test
    void 단어시험_문항을_읽는다() throws IOException {
        LessonManifest manifest = readWith(
                WORDS,
                withWordTests(item("B", 1, "kite") + ", {\"section\": \"B\", \"no\": 2, \"prompt\": \"분위기\","
                        + " \"answer\": \"atmosphere\", \"hint\": \"a\", \"accepted\": [\"atmosfere\"]}, "
                        + item("A", 1, "연")));

        assertThat(manifest.wordTests())
                .containsExactly(
                        new LessonManifest.WordTestItem("B", 1, "뜻", "kite", null, List.of()),
                        new LessonManifest.WordTestItem("B", 2, "분위기", "atmosphere", "a", List.of("atmosfere")),
                        new LessonManifest.WordTestItem("A", 1, "뜻", "연", null, List.of()));
    }

    @Test
    void 영역마다_문항_번호가_1부터_이어지지_않으면_거부() {
        assertRejected(WORDS, withWordTests(item("B", 1, "kite") + ", " + item("B", 3, "puddle")), "단어시험 B 2");
        assertRejected(WORDS, withWordTests(item("B", 1, "kite") + ", " + item("B", 1, "puddle")), "단어시험 B 2");
    }

    @Test
    void 모르는_영역은_거부() {
        assertRejected(WORDS, withWordTests(item("E", 1, "kite")), "모르는 영역");
        assertRejected(WORDS, withWordTests(item("b", 1, "kite")), "모르는 영역");
    }

    @Test
    void 허용_답이_정답과_같거나_겹치면_거부() {
        String same =
                "{\"section\": \"B\", \"no\": 1, \"prompt\": \"뜻\", \"answer\": \"kite\", \"accepted\": [\"kite\"]}";
        String twice =
                "{\"section\": \"B\", \"no\": 1, \"prompt\": \"뜻\", \"answer\": \"kite\", \"accepted\": [\"kyte\", \"kyte\"]}";
        assertRejected(WORDS, withWordTests(same), "허용 답");
        assertRejected(WORDS, withWordTests(twice), "허용 답");
    }

    @Test
    void 단어시험_칸_길이가_한도를_넘으면_거부() {
        assertRejected(WORDS, withWordTests(item("B", 1, "a".repeat(101))), "answer");
        assertRejected(WORDS, withWordTests(item("B", 1, "")), "answer");
        assertRejected(WORDS, withWordTests(full("뜻".repeat(201), "kite", null, "")), "prompt");
        assertRejected(WORDS, withWordTests(full("뜻", "kite", "k".repeat(51), "")), "hint");
        assertRejected(WORDS, withWordTests(full("뜻", "kite", "", "")), "hint");
        assertRejected(WORDS, withWordTests(full("뜻", "kite", null, "\"" + "k".repeat(101) + "\"")), "accepted");
    }

    @Test
    void 단어시험_빈_목록은_null과_다르다() throws IOException {
        // [] = 그 회차 문항 전부 삭제, 칸 없음(null) = 건드리지 않음
        assertThat(readWith(WORDS, withWordTests("")).wordTests()).isNotNull().isEmpty();
    }

    @Test
    void 한_글자_hint는_정답의_첫_글자여야_한다() throws IOException {
        assertRejected(WORDS, withWordTests(full("분위기", "atmosphere", "\"s\"", "")), "첫 글자");
        assertThat(readWith(WORDS, withWordTests(full("분위기", "atmosphere", "\"A\"", "")))
                        .wordTests())
                .as("대소문자는 가리지 않는다")
                .hasSize(1);
        assertThat(readWith(WORDS, withWordTests(full("가리키다", "point to", "\"p... t...\"", "")))
                        .wordTests())
                .as("숙어의 낱말별 hint는 검사하지 않는다")
                .hasSize(1);
    }

    /** prompt·answer는 문자열, hint는 JSON 값 그대로(null·"\"a\""), accepted는 JSON 배열 안쪽 */
    private static String full(String prompt, String answer, String hint, String accepted) {
        String hintJson =
                hint == null ? "null" : hint.isEmpty() ? "\"\"" : hint.startsWith("\"") ? hint : "\"" + hint + "\"";
        return "{\"section\": \"B\", \"no\": 1, \"prompt\": \"" + prompt + "\", \"answer\": \"" + answer
                + "\", \"hint\": " + hintJson + ", \"accepted\": [" + accepted + "]}";
    }

    private LessonManifest readWith(String from, String to) throws IOException {
        String text = fixture();
        if (from != null) {
            assertThat(text).contains(from);
            text = text.replace(from, to);
        }
        Path file = dir.resolve("L1-B1-01.json");
        Files.writeString(file, text, StandardCharsets.UTF_8);
        return reader.read(file);
    }

    private void assertRejected(String from, String to, String messagePart) {
        assertThatThrownBy(() -> readWith(from, to))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("L1-B1-01")
                .hasMessageContaining(messagePart);
    }

    private static String fixture() throws IOException {
        try (var in = LessonManifestTest.class.getResourceAsStream(FIXTURE)) {
            // Windows 체크아웃(CRLF)에서도 줄을 걸친 치환이 맞도록
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
    }
}
