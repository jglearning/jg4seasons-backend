package com.jglearning.jg4seasons.content.importer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * 회차 manifest 파일({@code {회차 코드}.json})을 읽는다. 모르는 칸·검사 위반은 IllegalArgumentException(메시지 앞에 파일 이름).
 *
 * <p>스크립트가 만든 파일만 읽으므로 앱의 API용 JsonMapper와 따로 둔다.
 */
public class ManifestReader {

    private final JsonMapper mapper = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build();

    public LessonManifest read(Path file) {
        String name = file.getFileName().toString();
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file);
        } catch (IOException e) {
            throw new UncheckedIOException("manifest를 읽지 못했다: " + name, e);
        }
        LessonManifest manifest;
        try {
            manifest = mapper.readValue(bytes, LessonManifest.class);
        } catch (JacksonException e) {
            IllegalArgumentException invalid = findInvalid(e);
            String reason = invalid != null ? invalid.getMessage() : e.getOriginalMessage();
            String path = e.getPathReference();
            String at = path == null || path.isEmpty() ? "" : " (위치 " + path + ")";
            throw new IllegalArgumentException(name + ": " + reason + at, e);
        }
        if (!name.equals(manifest.code() + ".json")) {
            throw new IllegalArgumentException(name + ": 파일 이름과 회차 코드 " + manifest.code() + "가 다르다");
        }
        return manifest;
    }

    /** 레코드 생성자의 검사 위반은 Jackson 예외의 원인으로 들어 있다 */
    private static IllegalArgumentException findInvalid(Throwable e) {
        for (Throwable cause = e.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof IllegalArgumentException invalid) {
                return invalid;
            }
        }
        return null;
    }
}
