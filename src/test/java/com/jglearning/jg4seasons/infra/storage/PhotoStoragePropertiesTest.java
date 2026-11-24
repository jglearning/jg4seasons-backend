package com.jglearning.jg4seasons.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** 사진 저장소 설정: 기본은 꺼짐(none). s3·local로 켰는데 칸이 비면 서버가 뜨지 않는다 */
class PhotoStoragePropertiesTest {

    @Test
    void 기본은_꺼짐() {
        assertThat(new PhotoStorageProperties(null, null, null, null).type())
                .isEqualTo(PhotoStorageProperties.Type.NONE);
    }

    @Test
    void s3인데_버킷이_없거나_형식이_다르면_거부() {
        assertThatThrownBy(() -> new PhotoStorageProperties(PhotoStorageProperties.Type.S3, "", "ap-northeast-2", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bucket");
        assertThatThrownBy(() -> new PhotoStorageProperties(
                        PhotoStorageProperties.Type.S3, "Bad_Bucket", "ap-northeast-2", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new PhotoStorageProperties(
                                PhotoStorageProperties.Type.S3, "jg4seasons-prod-homework-123456789012", null, null)
                        .region())
                .isEqualTo("ap-northeast-2");
    }

    @Test
    void local인데_폴더가_없으면_거부() {
        assertThatThrownBy(() -> new PhotoStorageProperties(PhotoStorageProperties.Type.LOCAL, null, null, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("local-dir");
    }
}
