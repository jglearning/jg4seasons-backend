package com.jglearning.jg4seasons.teacher.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 4.3·4.4 지문 한 줄(ADR 0034): 목소리를 섞어 들은 것까지 합친 값. 학생 회차 상세(2.3)의 지문 값과 같다. */
public record TeacherPassageSummary(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "지문 번호 1~6")
        int no,

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                minimum = "0",
                maximum = "1",
                description = "지문 청취율 0~1 — 여러 목소리로 들은 구간을 합친 청취율과 목소리 하나의 최댓값 중 큰 쪽. 안 들었으면 0")
        double coverage,

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "지문 완료 — 목소리 하나를 기준 이상 들었거나 합친 청취율이 기준 이상. 한 번 되면 유지")
        boolean completed) {}
