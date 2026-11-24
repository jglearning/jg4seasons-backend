package com.jglearning.jg4seasons.infra.storage;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * S3 비공개 버킷. 서명은 서버 안에서 계산하고(presign은 네트워크 없음), HeadObject·GetObject만 S3를 부른다. 자격은 기본 체인(운영은 인스턴스 역할). 만료 시각은
 * 주입된 시계로 계산한다(SDK 서명 시각은 시스템 시계 — 둘의 차이는 초 단위).
 */
class S3PhotoStorage implements PhotoStorage {

    private static final int NOT_FOUND = 404;

    private final S3Client client;
    private final S3Presigner presigner;
    private final String bucket;
    private final Clock clock;

    S3PhotoStorage(S3Client client, S3Presigner presigner, String bucket, Clock clock) {
        this.client = client;
        this.presigner = presigner;
        this.bucket = bucket;
        this.clock = clock;
    }

    @Override
    public UploadTicket presignPut(String key, String contentType, Duration ttl) {
        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build();
        URI url = toUri(presigner
                .presignPutObject(PutObjectPresignRequest.builder()
                        .signatureDuration(ttl)
                        .putObjectRequest(put)
                        .build())
                .url()
                .toString());
        return new UploadTicket(
                url, Map.of("Content-Type", contentType), clock.instant().plus(ttl));
    }

    @Override
    public Optional<StoredPhoto> head(String key) {
        try {
            HeadObjectResponse head = client.headObject(
                    HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return Optional.of(new StoredPhoto(head.contentLength(), head.contentType()));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (S3Exception e) {
            // HeadObject는 본문이 없어 없는 키도 NoSuchKey가 아닌 404 S3Exception으로 올 수 있다
            if (e.statusCode() == NOT_FOUND) {
                return Optional.empty();
            }
            throw e;
        }
    }

    /** 크기를 먼저 보고(넘으면 내려받지 않음), 받을 때도 상한 + 1바이트까지만 범위로 — 그 사이 더 큰 파일로 바뀌었어도 메모리에 다 올리지 않는다 */
    @Override
    public byte[] read(String key, long maxBytes) {
        StoredPhoto photo = head(key).orElseThrow(() -> new NoSuchElementException("사진이 없다"));
        if (photo.contentLength() > maxBytes) {
            throw new PhotoTooLargeException(maxBytes);
        }
        byte[] bytes;
        try {
            bytes = client.getObjectAsBytes(GetObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .range("bytes=0-" + maxBytes)
                            .build())
                    .asByteArray();
        } catch (NoSuchKeyException e) {
            throw new NoSuchElementException("사진이 없다");
        }
        if (bytes.length > maxBytes) {
            throw new PhotoTooLargeException(maxBytes);
        }
        return bytes;
    }

    @Override
    public Optional<URI> presignGet(String key, Duration ttl) {
        GetObjectRequest get =
                GetObjectRequest.builder().bucket(bucket).key(key).build();
        return Optional.of(toUri(presigner
                .presignGetObject(GetObjectPresignRequest.builder()
                        .signatureDuration(ttl)
                        .getObjectRequest(get)
                        .build())
                .url()
                .toString()));
    }

    /** 빈 종료 때 Spring이 부른다(추론된 close) */
    public void close() {
        presigner.close();
        client.close();
    }

    private static URI toUri(String url) {
        try {
            return new URI(url);
        } catch (URISyntaxException e) {
            // 메시지에 주소(서명·키)를 넣지 않는다
            throw new IllegalStateException("S3 서명 주소를 만들 수 없다");
        }
    }
}
