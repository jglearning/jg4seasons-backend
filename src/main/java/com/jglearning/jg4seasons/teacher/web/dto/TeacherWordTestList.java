package com.jglearning.jg4seasons.teacher.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** D-5 반 학생별 단어시험 최근 제출. 학생은 반의 현재 소속, 이름 순(매트릭스와 같은 학생) */
public record TeacherWordTestList(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "L2-B1-01")
        String lessonCode,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<TeacherWordTestStudent> students) {}
