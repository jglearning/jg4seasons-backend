package com.jglearning.jg4seasons.auth.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 1.4 응답. 토큰은 Set-Cookie(access·refresh·csrf)로만 나간다. */
public record StaffLoginResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UserInfo user) {

    /** 학생 로그인 응답의 UserInfo와 이름이 겹쳐 openapi에서 덮이지 않게 스키마 이름을 따로 둔다 */
    @Schema(name = "StaffUserInfo")
    public record UserInfo(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            long id,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String name,

            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    allowableValues = {"teacher", "admin", "superadmin"})
            String role,

            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    nullable = true,
                    description = "운영자(superadmin)는 null — 학원에 속하지 않는다")
            Long academyId) {}
}
