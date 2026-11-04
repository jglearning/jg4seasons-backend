package com.jglearning.jg4seasons.auth.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/** 1.2 응답. 토큰은 Set-Cookie(access·refresh·csrf)로만 나간다. */
public record StudentLoginResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UserInfo user,

        @JsonProperty("class") @Schema(name = "class", requiredMode = Schema.RequiredMode.REQUIRED)
        ClassRef clazz) {

    public record UserInfo(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            long id,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String name,

            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    allowableValues = {"student", "teacher", "admin", "superadmin"})
            String role) {}

    public record ClassRef(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            long id,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String name) {}
}
