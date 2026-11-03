package com.jglearning.jg4seasons.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * 완료 판정 임계값(기본 0.9)은 completion_rule 데이터로만 다룬다(ADR-07).
 *
 * <p>ArchUnit은 바이트코드의 상수 값을 보지 못해 소스 텍스트로 검사한다.
 */
class CompletionLiteralGuardTest {

    private static final Path MAIN_SOURCES = Path.of("src/main/java");
    private static final String COMPLETION_PACKAGE_PATH = "/listening/completion/";
    static final Pattern THRESHOLD_LITERAL = Pattern.compile("(?<![\\w.])0\\.90*(?!\\d)");

    @Test
    void 완료_임계값은_completion_패키지_밖에_없다() throws IOException {
        List<String> found = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN_SOURCES)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                if (file.toString().replace('\\', '/').contains(COMPLETION_PACKAGE_PATH)) {
                    continue;
                }
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) {
                    if (THRESHOLD_LITERAL.matcher(lines.get(i)).find()) {
                        found.add(file + ":" + (i + 1) + "  " + lines.get(i).strip());
                    }
                }
            }
        }
        assertThat(found).as("임계값 리터럴은 completion 패키지 밖에 둘 수 없다").isEmpty();
    }

    @Test
    void 검사기가_임계값_리터럴을_찾는다() {
        assertThat(THRESHOLD_LITERAL.matcher("double t = 0.9;").find()).isTrue();
        assertThat(THRESHOLD_LITERAL.matcher("new BigDecimal(\"0.900\")").find())
                .isTrue();
        assertThat(THRESHOLD_LITERAL.matcher("double t = 0.95;").find()).isFalse();
        assertThat(THRESHOLD_LITERAL.matcher("double t = 10.9;").find()).isFalse();
    }
}
