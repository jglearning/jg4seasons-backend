package com.jglearning.jg4seasons.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 로컬 폴더 저장소(로컬 프로필·로컬 확인용). 업로드 주소는 쓸 수 없는 자리표시 — 사진은 키 경로에 직접 넣는다 */
class LocalPhotoStorageTest {

    static final Instant NOW = Instant.parse("2026-11-26T05:05:00Z");
    static final String KEY = "homework/1/42/0b9e1c2d-3f4a-4b5c-8d6e-7f8091a2b3c4.jpg";

    @TempDir
    Path dir;

    LocalPhotoStorage storage;

    @BeforeEach
    void setUp() {
        storage = new LocalPhotoStorage(dir, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void 키_경로의_파일을_크기와_형식으로_돌려준다() throws IOException {
        Path file = dir.resolve(KEY);
        Files.createDirectories(file.getParent());
        Files.write(file, new byte[] {1, 2, 3});

        assertThat(storage.head(KEY)).contains(new StoredPhoto(3, "image/jpeg"));
        assertThat(storage.read(KEY, 3)).containsExactly(1, 2, 3);
        assertThat(storage.head(KEY.replace(".jpg", ".png"))).isEmpty();
        assertThatThrownBy(() -> storage.read("homework/1/42/none.jpg", 100))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void 상한을_넘는_사진은_읽지_않고_거부한다() throws IOException {
        Path file = dir.resolve(KEY);
        Files.createDirectories(file.getParent());
        Files.write(file, new byte[] {1, 2, 3});

        assertThatThrownBy(() -> storage.read(KEY, 2)).isInstanceOf(PhotoTooLargeException.class);
    }

    @Test
    void 업로드_주소는_자리표시이고_만료는_시계_기준() {
        UploadTicket ticket = storage.presignPut(KEY, "image/png", Duration.ofMinutes(5));

        assertThat(ticket.uploadUrl().toString()).isEqualTo("local-upload:" + KEY);
        assertThat(ticket.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
        assertThat(storage.presignGet(KEY, Duration.ofMinutes(5))).isEmpty();
    }

    @Test
    void 폴더_밖을_가리키는_키는_거부한다() {
        assertThatThrownBy(() -> storage.head("homework/../../outside.jpg"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.head("/etc/passwd")).isInstanceOf(IllegalArgumentException.class);
    }
}
