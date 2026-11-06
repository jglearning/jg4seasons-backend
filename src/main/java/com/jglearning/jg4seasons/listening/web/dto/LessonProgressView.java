package com.jglearning.jg4seasons.listening.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 회차 진도(2.1·2.2). 기록이 없어도 null이 아니라 not_started·0·지문 수(결정 B). */
@Schema(name = "LessonProgressView")
public record LessonProgressView(
        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"not_started", "in_progress", "completed"})
        String status,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "완료한 지문 수")
        int passagesDone,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "지문 수(콘텐츠가 아직 없으면 0)")
        int passagesTotal) {}
