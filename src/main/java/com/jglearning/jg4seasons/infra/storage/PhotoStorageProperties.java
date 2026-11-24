package com.jglearning.jg4seasons.infra.storage;

import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 숙제 사진 저장소 설정(jg4seasons.word-test.storage.*). 기본은 꺼짐(none) — 운영에 버킷 값이 생기기 전 배포가 막히지 않게. s3·local로 켰는데 칸이 비면 서버가
 * 뜨지 않는다.
 *
 * @param type none(꺼짐 — 쓰면 IllegalStateException) · s3 · local · memory(테스트)
 * @param bucket S3 버킷 이름. JG4SEASONS_WORD_TEST_BUCKET
 * @param region S3 지역, 기본 ap-northeast-2
 * @param localDir 로컬 폴더(type local)
 */
@ConfigurationProperties("jg4seasons.word-test.storage")
public record PhotoStorageProperties(Type type, String bucket, String region, String localDir) {

    public enum Type {
        NONE,
        S3,
        LOCAL,
        MEMORY
    }

    /** S3 버킷 이름 규칙(소문자·숫자·점·하이픈, 3~63자) */
    private static final Pattern BUCKET = Pattern.compile("^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$");

    static final String DEFAULT_REGION = "ap-northeast-2";

    public PhotoStorageProperties {
        type = type == null ? Type.NONE : type;
        region = region == null || region.isBlank() ? DEFAULT_REGION : region;
        if (type == Type.S3 && (bucket == null || !BUCKET.matcher(bucket).matches())) {
            throw new IllegalArgumentException("jg4seasons.word-test.storage.bucket이 비었거나 S3 버킷 이름 형식이 아니다");
        }
        if (type == Type.LOCAL && (localDir == null || localDir.isBlank())) {
            throw new IllegalArgumentException("jg4seasons.word-test.storage.local-dir이 비었다");
        }
    }
}
