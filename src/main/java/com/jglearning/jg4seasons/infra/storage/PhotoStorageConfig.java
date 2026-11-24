package com.jglearning.jg4seasons.infra.storage;

import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** 숙제 사진 저장소를 설정값으로 고른다. 테스트(memory)는 테스트 설정의 @Primary 빈이 이 빈을 가린다 */
@Configuration
@EnableConfigurationProperties(PhotoStorageProperties.class)
class PhotoStorageConfig {

    private static final Logger log = LoggerFactory.getLogger(PhotoStorageConfig.class);

    @Bean
    PhotoStorage photoStorage(PhotoStorageProperties properties, Clock clock) {
        return switch (properties.type()) {
            case S3 -> {
                Region region = Region.of(properties.region());
                DefaultCredentialsProvider credentials =
                        DefaultCredentialsProvider.builder().build();
                S3Client client = S3Client.builder()
                        .region(region)
                        .credentialsProvider(credentials)
                        .httpClientBuilder(UrlConnectionHttpClient.builder())
                        .build();
                S3Presigner presigner = S3Presigner.builder()
                        .region(region)
                        .credentialsProvider(credentials)
                        .build();
                log.info("숙제 사진 저장소: S3 {} ({})", properties.bucket(), region);
                yield new S3PhotoStorage(client, presigner, properties.bucket(), clock);
            }
            case LOCAL -> {
                log.info("숙제 사진 저장소: 로컬 폴더 {}", properties.localDir());
                yield new LocalPhotoStorage(Path.of(properties.localDir()), clock);
            }
            case NONE, MEMORY -> {
                log.info("숙제 사진 저장소 꺼짐 — 단어시험 사진 API는 쓸 수 없다");
                yield NoPhotoStorage.INSTANCE;
            }
        };
    }

    /** 꺼진 저장소. 쓰면 IllegalStateException(500) — 기능을 켠 학원이 있는데 저장소 설정이 빠진 운영 실수를 드러낸다 */
    enum NoPhotoStorage implements PhotoStorage {
        INSTANCE;

        @Override
        public UploadTicket presignPut(String key, String contentType, Duration ttl) {
            throw off();
        }

        @Override
        public Optional<StoredPhoto> head(String key) {
            throw off();
        }

        @Override
        public byte[] read(String key) {
            throw off();
        }

        @Override
        public Optional<URI> presignGet(String key, Duration ttl) {
            throw off();
        }

        private static IllegalStateException off() {
            return new IllegalStateException("숙제 사진 저장소가 꺼져 있다(jg4seasons.word-test.storage.type)");
        }
    }
}
