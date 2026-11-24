package com.jglearning.jg4seasons.wordtest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 숙제 사진 키는 요청한 학생 자신의 경로만 받는다(학원 1, 학생 42) */
class ImageKeysTest {

    static final String UUID = "0b9e1c2d-3f4a-4b5c-8d6e-7f8091a2b3c4";

    @ParameterizedTest
    @ValueSource(strings = {"homework/1/42/" + UUID + ".jpg", "homework/1/42/" + UUID + ".png"})
    void 내_경로의_키(String key) {
        assertThat(ImageKeys.belongsTo(key, 1, 42)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "homework/1/43/" + UUID + ".jpg", // 다른 학생
                "homework/2/42/" + UUID + ".jpg", // 다른 학원
                "homework/1/42/../43/" + UUID + ".jpg", // 경로 조작
                "homework/1/42/../../2/42/" + UUID + ".jpg",
                "homework/1/420/" + UUID + ".jpg", // 앞자리만 같은 학생 id
                "homework/11/42/" + UUID + ".jpg",
                "homework/1/42/0B9E1C2D-3F4A-4B5C-8D6E-7F8091A2B3C4.jpg", // 대문자 UUID
                "homework/1/42/" + UUID + ".jpeg",
                "homework/1/42/" + UUID + ".gif",
                "homework/1/42/" + UUID + ".JPG",
                "/homework/1/42/" + UUID + ".jpg",
                "homework/1/42/" + UUID + ".jpg ",
                "homework/1/42/sub/" + UUID + ".jpg",
                "homework/1/42/" + UUID,
                "",
            })
    void 남의_경로이거나_형식이_다르면_아니다(String key) {
        assertThat(ImageKeys.belongsTo(key, 1, 42)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"image/jpeg", "image/png"})
    void 새_키는_내_경로이고_다시_검사를_통과한다(String contentType) {
        String key = ImageKeys.newKey(1, 42, contentType).orElseThrow();

        assertThat(ImageKeys.belongsTo(key, 1, 42)).isTrue();
    }
}
