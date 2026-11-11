package com.jglearning.jg4seasons.system.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 7.1 학원 목록, id 순. */
public record AcademyListResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<AcademyResponse> items) {}
