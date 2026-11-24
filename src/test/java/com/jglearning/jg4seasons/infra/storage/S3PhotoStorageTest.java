package com.jglearning.jg4seasons.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** S3 presigned 주소(서명은 서버 안에서 계산 — 네트워크 없음). 자격은 가짜 고정값 */
class S3PhotoStorageTest {

    static final Instant NOW = Instant.parse("2026-11-26T05:05:00Z");
    static final String BUCKET = "test-homework-bucket";
    static final String KEY = "homework/1/42/0b9e1c2d-3f4a-4b5c-8d6e-7f8091a2b3c4.jpg";

    S3Presigner presigner;
    S3Client client;
    S3PhotoStorage storage;

    @BeforeEach
    void setUp() {
        StaticCredentialsProvider credentials =
                StaticCredentialsProvider.create(AwsBasicCredentials.create("AKIATESTTESTTEST", "secret"));
        presigner = S3Presigner.builder()
                .region(Region.AP_NORTHEAST_2)
                .credentialsProvider(credentials)
                .build();
        client = S3Client.builder()
                .region(Region.AP_NORTHEAST_2)
                .credentialsProvider(credentials)
                .build();
        storage = new S3PhotoStorage(client, presigner, BUCKET, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() {
        presigner.close();
        client.close();
    }

    @Test
    void 업로드_주소는_버킷_키_5분_만료와_content_type_서명을_담는다() {
        UploadTicket ticket = storage.presignPut(KEY, "image/jpeg", Duration.ofMinutes(5));

        URI url = ticket.uploadUrl();
        assertThat(url.getScheme()).isEqualTo("https");
        assertThat(url.getHost()).startsWith(BUCKET + ".s3.");
        assertThat(url.getPath()).isEqualTo("/" + KEY);
        assertThat(url.getQuery()).contains("X-Amz-Expires=300").contains("X-Amz-SignedHeaders=content-type;host");
        assertThat(ticket.headers()).isEqualTo(Map.of("Content-Type", "image/jpeg"));
        assertThat(ticket.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
    }

    @Test
    void 보기_주소는_GET_서명() {
        URI url = storage.presignGet(KEY, Duration.ofMinutes(10)).orElseThrow();

        assertThat(url.getPath()).isEqualTo("/" + KEY);
        assertThat(url.getQuery()).contains("X-Amz-Expires=600").contains("X-Amz-SignedHeaders=host");
    }
}
