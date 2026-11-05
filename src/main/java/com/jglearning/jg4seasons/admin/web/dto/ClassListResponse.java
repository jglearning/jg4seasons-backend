package com.jglearning.jg4seasons.admin.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 반 목록(id 순). */
public record ClassListResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ClassResponse> items,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int total) {}
