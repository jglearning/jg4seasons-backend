package com.jglearning.jg4seasons.listening.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.academy.api.LicenseStatus;
import com.jglearning.jg4seasons.listening.web.dto.StudentClassInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** openapi의 licenseStatus 값 목록은 손으로 적었다 — LicenseStatus에 상태를 더하거나 값을 바꾸면 여기서 걸린다(명세 테스트는 생성본과 커밋본만 비교). */
class StudentClassInfoSchemaTest {

    @Test
    void licenseStatus_허용_값은_LicenseStatus_값과_같다() throws NoSuchFieldException {
        Schema schema = StudentClassInfo.class.getDeclaredField("licenseStatus").getAnnotation(Schema.class);

        assertThat(schema).isNotNull();
        assertThat(schema.allowableValues())
                .containsExactlyInAnyOrderElementsOf(Arrays.stream(LicenseStatus.values())
                        .map(LicenseStatus::value)
                        .toList());
    }
}
