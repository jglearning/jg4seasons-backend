package com.jglearning.jg4seasons.admin.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 5.2 반의 현재 소속 학생(이름 순). */
public record StudentListResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<StudentResponse> items,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int total) {}
