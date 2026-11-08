package com.jglearning.jg4seasons.teacher.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 회차 짧은 참조(교사 화면 공용). */
public record TeacherLessonRef(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "L2-B1-05")
        String code,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Reading Day 5")
        String title) {}
