package com.jglearning.jg4seasons.auth.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 1.7 내 정보. refresh(1.5) 성공 응답도 같은 형태다. */
public record MeResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"student", "teacher", "admin", "superadmin"})
        String role,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) AcademyRef academy) {

    public record AcademyRef(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            long id,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String name) {}
}
