package com.jglearning.jg4seasons.wordtest;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** 숙제 사진 키 {@code homework/{academyId}/{studentId}/{UUID}.{jpg|png}}. 학생 경로가 키에 들어가 남의 사진을 제출할 수 없게 한다 */
final class ImageKeys {

    /** 받는 형식 → 확장자 */
    static final Map<String, String> EXTENSIONS = Map.of("image/jpeg", "jpg", "image/png", "png");

    private ImageKeys() {}

    /** JPEG·PNG가 아니면 empty(대소문자까지 그대로) */
    static Optional<String> newKey(long academyId, long studentId, String contentType) {
        String extension = EXTENSIONS.get(contentType);
        if (extension == null) {
            return Optional.empty();
        }
        return Optional.of("homework/" + academyId + "/" + studentId + "/" + UUID.randomUUID() + "." + extension);
    }
}
