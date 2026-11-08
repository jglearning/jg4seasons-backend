package com.jglearning.jg4seasons.teacher.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 4.8 개인 코드 재발급 결과. 평문은 이 응답에서만, 다시 볼 수 없다(ADR-22). */
public record PersonalCodeResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long studentId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "숫자 4자리(0으로 시작 가능)", example = "0482")
        String personalCode) {

    /** 로그에 개인 코드가 남지 않게 */
    @Override
    public String toString() {
        return "PersonalCodeResponse[studentId=" + studentId + "]";
    }
}
