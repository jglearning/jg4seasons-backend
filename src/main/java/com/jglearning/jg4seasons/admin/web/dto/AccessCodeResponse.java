package com.jglearning.jg4seasons.admin.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** 재발급된 가입 코드. */
public record AccessCodeResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "새 가입 코드 8자")
        String accessCode,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "발급 시각(UTC, 초 단위)")
        Instant codeIssuedAt) {}
