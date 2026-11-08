package com.jglearning.jg4seasons.teacher.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 반의 레벨 표시 정보(교사 화면 공용). */
public record TeacherClassLevel(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "L2")
        String code,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "그린")
        String nameKo,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "#1F7A4F")
        String colorHex) {}
