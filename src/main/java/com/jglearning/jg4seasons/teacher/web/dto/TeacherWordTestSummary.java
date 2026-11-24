package com.jglearning.jg4seasons.teacher.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** 목록의 제출 요약(문항 없음). 상세는 D-6 */
public record TeacherWordTestSummary(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "제출 id(D-6 경로)")
        long id,

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "pending·grading이면 채점 중, graded·failed에서 멈춤. confirming·rejected는 2단계(지금은 오지 않음)",
                allowableValues = {"pending", "grading", "confirming", "graded", "rejected", "failed"})
        String status,

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                nullable = true,
                description = "맞은(correct) 문항 수. graded가 아니면 null")
        Integer score,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "그 회차·영역의 문항 수(unclear 포함)")
        int total,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "그 학생·회차·영역의 몇 번째 제출(1부터)")
        int attemptNo,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "제출 시각(UTC)")
        Instant submittedAt) {}
