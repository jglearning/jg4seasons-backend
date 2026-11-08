package com.jglearning.jg4seasons.teacher.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 반의 권 표시 정보(교사 화면 공용). */
public record TeacherClassBook(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "L2-B1")
        String code,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Book 1")
        String label,

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"겨울", "봄", "여름", "가을"})
        String season) {}
