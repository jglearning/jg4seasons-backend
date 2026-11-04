package com.jglearning.jg4seasons.admin.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 교사·관리자 목록(id 순). */
public record TeacherListResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<TeacherResponse> items,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int total) {}
