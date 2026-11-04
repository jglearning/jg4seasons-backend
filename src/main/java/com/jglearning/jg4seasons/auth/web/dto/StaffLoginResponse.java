package com.jglearning.jg4seasons.auth.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 1.4 응답. 토큰은 Set-Cookie(access·refresh·csrf)로만 나간다. */
public record StaffLoginResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UserInfo user) {

    public record UserInfo(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            long id,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String name,

            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    allowableValues = {"teacher", "admin"})
            String role,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            long academyId) {}
}
