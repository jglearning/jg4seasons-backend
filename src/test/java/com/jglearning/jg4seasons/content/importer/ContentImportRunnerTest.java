package com.jglearning.jg4seasons.content.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.content.importer.ImportResult.Status;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** 폴더의 manifest를 회차마다 넣는다. 테스트 컨텍스트를 하나로 두려고 속성으로 켜지 않고 직접 만든다 */
@IntegrationTest
@Transactional
class ContentImportRunnerTest {

    @Autowired
    LessonImporter importer;

    @Autowired
    JdbcTemplate jdbc;

    @TempDir
    Path dir;

    @Test
    void 회차_하나가_실패해도_나머지를_넣고_종료_코드_1로_끝낸다() throws IOException {
        write("L1-B1-01");
        write("L1-B1-02");
        Files.writeString(dir.resolve("L1-B1-03.json"), "{", StandardCharsets.UTF_8); // 깨진 파일
        write("K-B4-01"); // 기준 데이터에 없는 회차
        Files.writeString(dir.resolve("readme.txt"), "json이 아니면 읽지 않는다", StandardCharsets.UTF_8);
        AtomicInteger exitCode = new AtomicInteger(-1);

        new ContentImportRunner(importer, dir, exitCode::set).run(new DefaultApplicationArguments());

        assertThat(exitCode).hasValue(1);
        assertThat(jdbc.queryForList(
                        "select l.code from passage p join lesson l on l.id = p.lesson_id where p.no = 1 and l.code like 'L1-B1-%'"
                                + " order by l.code",
                        String.class))
                .containsExactly("L1-B1-01", "L1-B1-02");
    }

    @Test
    void 결과를_파일_이름_순서로_돌려준다() throws IOException {
        write("L1-B1-02");
        write("L1-B1-01");
        Files.writeString(dir.resolve("L1-B1-03.json"), "{", StandardCharsets.UTF_8);
        ContentImportRunner runner = new ContentImportRunner(importer, dir, code -> {});

        List<ImportResult> first = runner.importAll();
        List<ImportResult> second = runner.importAll();

        assertThat(first)
                .extracting(ImportResult::code, ImportResult::status)
                .containsExactly(
                        tuple("L1-B1-01", Status.IMPORTED),
                        tuple("L1-B1-02", Status.IMPORTED),
                        tuple("L1-B1-03.json", Status.REJECTED));
        assertThat(second)
                .extracting(ImportResult::status)
                .containsExactly(Status.UNCHANGED, Status.UNCHANGED, Status.REJECTED);
        assertThat(first.get(2).reason()).contains("L1-B1-03.json");
    }

    @Test
    void 폴더에_manifest가_없으면_종료_코드_1() {
        AtomicInteger exitCode = new AtomicInteger(-1);

        new ContentImportRunner(importer, dir.resolve("없는 폴더"), exitCode::set).run(new DefaultApplicationArguments());

        assertThat(exitCode).hasValue(1);
    }

    private void write(String code) throws IOException {
        String text;
        try (var in = getClass().getResourceAsStream("/content-import/L1-B1-01.json")) {
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Files.writeString(dir.resolve(code + ".json"), text.replace("L1-B1-01", code), StandardCharsets.UTF_8);
    }
}
