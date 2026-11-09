package com.jglearning.jg4seasons.content.importer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

/**
 * 폴더의 회차 manifest({@code *.json})를 파일 이름 순서로 회차마다 넣고(회차마다 트랜잭션 하나) 결과를 로그로 남긴 뒤 앱을 끝낸다. 회차 하나가 실패해도 나머지는
 * 넣는다. 거부된 회차가 있거나 manifest가 없으면 종료 코드 1.
 *
 * <p>{@link ContentImportConfig}가 {@code jg4seasons.content-import.dir}이 있을 때만 만든다.
 */
public class ContentImportRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ContentImportRunner.class);

    private final LessonImporter importer;
    private final ManifestReader reader = new ManifestReader();
    private final Path dir;
    private final IntConsumer exit;

    public ContentImportRunner(LessonImporter importer, Path dir, IntConsumer exit) {
        this.importer = importer;
        this.dir = dir;
        this.exit = exit;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<ImportResult> results;
        try {
            results = importAll();
        } catch (UncheckedIOException e) {
            log.error("content import: manifest 폴더를 읽지 못했다: {}", dir, e);
            exit.accept(1);
            return;
        }
        long imported = count(results, ImportResult.Status.IMPORTED);
        long unchanged = count(results, ImportResult.Status.UNCHANGED);
        List<ImportResult> rejected = results.stream()
                .filter(r -> r.status() == ImportResult.Status.REJECTED)
                .toList();
        log.info(
                "content import: 파일 {} · IMPORTED {} · UNCHANGED {} · REJECTED {}",
                results.size(),
                imported,
                unchanged,
                rejected.size());
        rejected.forEach(r -> log.warn("content import: REJECTED {} — {}", r.code(), r.reason()));
        if (results.isEmpty()) {
            log.error("content import: manifest가 없다: {}", dir);
        }
        exit.accept(results.isEmpty() || !rejected.isEmpty() ? 1 : 0);
    }

    /** 파일마다 결과, 파일 이름 순. 읽을 수 없거나 넣다가 예외가 난 파일은 code 자리에 파일 이름을 둔 REJECTED */
    public List<ImportResult> importAll() {
        List<Path> files;
        try (Stream<Path> list = Files.list(dir)) {
            files = list.filter(f -> f.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        List<ImportResult> results = new ArrayList<>();
        for (Path file : files) {
            ImportResult result = importOne(file);
            log.info("content import: {} {}", result.code(), result.status());
            results.add(result);
        }
        return results;
    }

    private ImportResult importOne(Path file) {
        String name = file.getFileName().toString();
        LessonManifest manifest;
        try {
            manifest = reader.read(file);
        } catch (RuntimeException e) {
            return ImportResult.rejected(name, messageWith(name, e));
        }
        try {
            return importer.importLesson(manifest);
        } catch (RuntimeException e) {
            // 트랜잭션은 롤백됐다. DB 제약 위반 등
            log.error("content import: {} 넣다가 실패", name, e);
            return ImportResult.rejected(manifest.code(), messageWith(name, e));
        }
    }

    private static String messageWith(String name, RuntimeException e) {
        String message = String.valueOf(e.getMessage());
        return message.contains(name) ? message : name + ": " + message;
    }

    private static long count(List<ImportResult> results, ImportResult.Status status) {
        return results.stream().filter(r -> r.status() == status).count();
    }
}
