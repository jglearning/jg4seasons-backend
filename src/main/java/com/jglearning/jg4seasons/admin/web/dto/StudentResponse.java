package com.jglearning.jg4seasons.admin.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** 5.2 관리자용 학생 정보. 개인 코드 평문은 없다(발급·재발급 응답에서만). */
public record StudentResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        String school,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        String grade,

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"active", "inactive"},
                description = "inactive = 퇴원(기록은 남음)")
        String status,

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                nullable = true,
                description = "개인 코드 발급 시각(UTC). 코드가 없으면 null")
        Instant codeIssuedAt,

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                nullable = true,
                description = "마지막 로그인(UTC). 한 번도 없으면 null")
        Instant lastLoginAt) {}
