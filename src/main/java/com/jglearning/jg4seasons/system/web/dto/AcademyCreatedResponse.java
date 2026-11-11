package com.jglearning.jg4seasons.system.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 7.2 응답: 만든 학원과 첫 관리자(비밀번호 없음). */
public record AcademyCreatedResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String slug,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String timezone,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean licenseEnforced,

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"active", "suspended"})
        String status,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) AdminRef admin) {

    @Schema(name = "AcademyAdminRef")
    public record AdminRef(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            long id,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String loginId,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String name) {}
}
