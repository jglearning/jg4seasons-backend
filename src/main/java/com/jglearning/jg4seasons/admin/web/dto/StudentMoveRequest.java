package com.jglearning.jg4seasons.admin.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** 5.2 학생 반 이동(지금 반에서 나가고 새 반에 들어감). */
public record StudentMoveRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "같은 학원의 반") @NotNull
        Long toClassId) {}
