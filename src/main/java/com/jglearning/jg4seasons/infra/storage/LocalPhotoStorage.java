package com.jglearning.jg4seasons.infra.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * 로컬 폴더 저장소(로컬 프로필·로컬 확인용). 업로드 주소는 쓸 수 없는 자리표시({@code local-upload:<키>}) — 프론트 로컬은 목 업로드를 쓰고, 로컬 확인은 사진을
 * {@code <폴더>/<키>}에 직접 복사한다. 형식은 확장자로 정한다.
 */
class LocalPhotoStorage implements PhotoStorage {

    private final Path root;
    private final Clock clock;

    LocalPhotoStorage(Path root, Clock clock) {
        this.root = root.toAbsolutePath().normalize();
        this.clock = clock;
    }

    @Override
    public UploadTicket presignPut(String key, String contentType, Duration ttl) {
        resolve(key);
        return new UploadTicket(
                URI.create("local-upload:" + key),
                Map.of("Content-Type", contentType),
                clock.instant().plus(ttl));
    }

    @Override
    public Optional<StoredPhoto> head(String key) {
        Path file = resolve(key);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(new StoredPhoto(Files.size(file), typeOf(key)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public byte[] read(String key, long maxBytes) {
        try {
            Path file = resolve(key);
            if (Files.size(file) > maxBytes) {
                throw new PhotoTooLargeException(maxBytes);
            }
            return Files.readAllBytes(file);
        } catch (NoSuchFileException e) {
            throw new NoSuchElementException("사진이 없다");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public Optional<URI> presignGet(String key, Duration ttl) {
        return Optional.empty();
    }

    /** 폴더 밖을 가리키는 키(절대 경로·..)는 IllegalArgumentException */
    private Path resolve(String key) {
        Path relative = Path.of(key);
        Path file = root.resolve(relative).normalize();
        if (relative.isAbsolute() || key.startsWith("/") || !file.startsWith(root)) {
            throw new IllegalArgumentException("저장소 폴더 밖을 가리키는 키");
        }
        return file;
    }

    private static String typeOf(String key) {
        if (key.endsWith(".jpg")) {
            return "image/jpeg";
        }
        if (key.endsWith(".png")) {
            return "image/png";
        }
        return null;
    }
}
