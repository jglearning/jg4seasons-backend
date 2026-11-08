package com.jglearning.jg4seasons.admin.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 개인 코드 평문. 발급·재발급 응답에서만 나가고 다시 볼 수 없다(ADR-22). */
public record PersonalCodeItem(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "숫자 4자리(0으로 시작 가능)", example = "0482")
        String personalCode) {

    /** 로그에 개인 코드가 남지 않게 */
    @Override
    public String toString() {
        return "PersonalCodeItem[id=" + id + "]";
    }
}
