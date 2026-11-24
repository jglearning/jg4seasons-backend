package com.jglearning.jg4seasons.wordtest.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** D-2 제출 결과. 채점은 따로 돌고, 결과는 제출 조회(D-3)를 2초 간격으로 */
@Schema(name = "WordTestSubmitResponse")
public record SubmitResponse(
        @Schema(description = "제출 id", requiredMode = Schema.RequiredMode.REQUIRED)
        long id,

        @Schema(
                description = "제출 상태. 새 제출은 pending",
                allowableValues = {"pending", "grading", "confirming", "graded", "rejected", "failed"},
                requiredMode = Schema.RequiredMode.REQUIRED)
        String status) {}
