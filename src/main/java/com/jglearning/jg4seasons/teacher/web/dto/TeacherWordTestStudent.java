package com.jglearning.jg4seasons.teacher.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 학생 한 명과 그 회차·영역의 마지막 시도 */
public record TeacherWordTestStudent(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long studentId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "김민준")
        String name,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "마지막 시도. 낸 적이 없으면 null")
        TeacherWordTestSummary submission) {}
