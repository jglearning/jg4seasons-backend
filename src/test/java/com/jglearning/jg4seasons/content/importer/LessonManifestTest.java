package com.jglearning.jg4seasons.content.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
        assertThat(manifest.words()).extracting(LessonManifest.Word::pos).containsExactly("n", "phr");
        assertThat(manifest.words().get(1).exampleTrack().fileKey())
                .isEqualTo("audio/L1-B1-01/L1-B1-01_w02_ex_us_w.mp3");
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
        assertRejected("\"title\": \"시험용 회차\"", "\"title\": \"" + "가".repeat(101) + "\"", "L1-B1-01");
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
