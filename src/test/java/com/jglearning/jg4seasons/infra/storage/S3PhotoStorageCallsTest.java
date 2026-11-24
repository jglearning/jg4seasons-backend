package com.jglearning.jg4seasons.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * S3를 부르는 쪽(HeadObject·GetObject)의 오류 변환과 크기 상한. S3Client는 가짜(단위 테스트 — 통합 테스트의 빈 교체 아님). 없는 키(404)만 "없음", 권한 오류(403)는 그대로
 * 던진다 — 권한 설정 실수가 400으로 묻히지 않게
 */
class S3PhotoStorageCallsTest {

    static final String KEY = "homework/1/42/0b9e1c2d-3f4a-4b5c-8d6e-7f8091a2b3c4.jpg";

    S3Client client;
    S3PhotoStorage storage;

    @BeforeEach
    void setUp() {
        client = mock(S3Client.class);
        storage = new S3PhotoStorage(
                client,
                mock(S3Presigner.class),
                "test-bucket",
                Clock.fixed(Instant.parse("2026-11-26T03:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void head는_크기와_형식을_돌려준다() {
        when(client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder()
                        .contentLength(1234L)
                        .contentType("image/jpeg")
                        .build());

        assertThat(storage.head(KEY)).contains(new StoredPhoto(1234, "image/jpeg"));
    }

    @Test
    void head는_없는_키_404면_empty() {
        when(client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().statusCode(404).build());
        assertThat(storage.head(KEY)).isEmpty();

        when(client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).build());
        assertThat(storage.head(KEY)).isEmpty();
    }

    @Test
    void head는_권한_오류_403을_그대로_던진다() {
        when(client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(403).build());

        assertThatThrownBy(() -> storage.head(KEY)).isInstanceOf(S3Exception.class);
    }

    @Test
    void read는_상한을_넘는_사진을_내려받지_않고_거부한다() {
        when(client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().contentLength(101L).build());

        assertThatThrownBy(() -> storage.read(KEY, 100)).isInstanceOf(PhotoTooLargeException.class);
        verify(client, never()).getObjectAsBytes(any(GetObjectRequest.class));
    }

    @Test
    void read는_상한_바이트까지만_범위로_받고_그_사이_커졌으면_거부한다() {
        when(client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().contentLength(100L).build());
        when(client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(
                        ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), new byte[101]));

        assertThatThrownBy(() -> storage.read(KEY, 100)).isInstanceOf(PhotoTooLargeException.class);
        ArgumentCaptor<GetObjectRequest> request = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(client).getObjectAsBytes(request.capture());
        assertThat(request.getValue().range()).isEqualTo("bytes=0-100");
    }

    @Test
    void read는_상한_안이면_바이트를_돌려주고_없으면_NoSuchElement() {
        when(client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().contentLength(3L).build());
        when(client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(
                        ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), new byte[] {1, 2, 3}));
        assertThat(storage.read(KEY, 100)).containsExactly(1, 2, 3);

        when(client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().statusCode(404).build());
        assertThatThrownBy(() -> storage.read(KEY, 100)).isInstanceOf(NoSuchElementException.class);
    }
}
