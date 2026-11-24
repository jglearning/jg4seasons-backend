package com.jglearning.jg4seasons.wordtest;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/** 숙제 사진 키 {@code homework/{academyId}/{studentId}/{UUID}.{jpg|png}}. 학생 경로가 키에 들어가 남의 사진을 제출할 수 없게 한다 */
final class ImageKeys {

    /** 받는 형식 → 확장자 */
    static final Map<String, String> EXTENSIONS = Map.of("image/jpeg", "jpg", "image/png", "png");

    /** 학원·학생 다음의 파일 이름: 소문자 UUID + 확장자. 하위 경로·..·공백은 어디에도 들어갈 수 없다 */
    private static final Pattern FILE =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(?:jpg|png)$");

    private ImageKeys() {}

    /** 요청한 학생 자신의 경로({@code homework/{academyId}/{studentId}/})에 정확히 한 파일이면 true */
    static boolean belongsTo(String key, long academyId, long studentId) {
        String prefix = "homework/" + academyId + "/" + studentId + "/";
        return key != null
                && key.startsWith(prefix)
                && FILE.matcher(key.substring(prefix.length())).matches();
    }

    /** JPEG·PNG가 아니면 empty(대소문자까지 그대로) */
    static Optional<String> newKey(long academyId, long studentId, String contentType) {
        String extension = EXTENSIONS.get(contentType);
        if (extension == null) {
            return Optional.empty();
        }
        return Optional.of("homework/" + academyId + "/" + studentId + "/" + UUID.randomUUID() + "." + extension);
    }
}
