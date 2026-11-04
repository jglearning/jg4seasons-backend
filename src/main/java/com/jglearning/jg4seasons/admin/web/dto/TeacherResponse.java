package com.jglearning.jg4seasons.admin.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/** 교사·관리자 계정. 비밀번호·해시 없음. */
public record TeacherResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "로그인 수단이 없으면 null")
        String loginId,

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"teacher", "admin"})
        String role,

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"active", "inactive"})
        String status,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "담당 반 id(오름차순)")
        List<Long> classIds,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "한 번도 로그인하지 않았으면 null(UTC)")
        Instant lastLoginAt) {}
