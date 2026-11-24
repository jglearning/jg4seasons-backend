package com.jglearning.jg4seasons.wordtest.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.jglearning.jg4seasons.wordtest.grading.Section;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/** D-3·D-4 제출. graded가 아니면 score·gradedAt은 null이고 items 칸은 아예 없다(정답이 새지 않게) */
@Schema(name = "WordTestSubmission")
public record SubmissionResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long id,

        @Schema(example = "L2-B1-05", requiredMode = Schema.RequiredMode.REQUIRED)
        String lessonCode,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Section section,

        @Schema(description = "그 학생·회차·영역의 몇 번째 제출(1부터)", requiredMode = Schema.RequiredMode.REQUIRED)
        int attemptNo,

        @Schema(
                description = "pending·grading이면 2초 뒤 다시 조회, graded·failed에서 멈춤. confirming·rejected는 2단계(지금은 오지 않음)",
                allowableValues = {"pending", "grading", "confirming", "graded", "rejected", "failed"},
                requiredMode = Schema.RequiredMode.REQUIRED)
        String status,

        @Schema(
                description = "맞은(correct) 문항 수. graded가 아니면 null",
                nullable = true,
                requiredMode = Schema.RequiredMode.REQUIRED)
        Integer score,

        @Schema(description = "그 회차·영역의 문항 수(unclear 포함)", requiredMode = Schema.RequiredMode.REQUIRED)
        int total,

        @Schema(description = "제출 시각(UTC)", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant submittedAt,

        @Schema(
                description = "채점 끝난 시각(UTC). graded가 아니면 null",
                nullable = true,
                requiredMode = Schema.RequiredMode.REQUIRED)
        Instant gradedAt,

        @Schema(
                description = "문항별 결과, 번호 순. graded일 때만 있다(그 밖에는 칸 자체가 없음)",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @JsonInclude(JsonInclude.Include.NON_NULL)
        List<Item> items) {

    /** 문항 하나 */
    @Schema(name = "WordTestItemResult")
    public record Item(
            @Schema(description = "책의 문항 번호", requiredMode = Schema.RequiredMode.REQUIRED)
            int no,

            @Schema(description = "문제로 준 쪽(B는 우리말 뜻)", example = "아름다운", requiredMode = Schema.RequiredMode.REQUIRED)
            String prompt,

            @Schema(
                    description = "학생이 쓴 글자(읽은 그대로). 빈칸·못 읽음이면 \"\"",
                    example = "beautful",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            String written,

            @Schema(
                    description = "correct·wrong·blank·unclear. unclear = 글자가 흐려 판단 못 함(score에 들어가지 않음)",
                    allowableValues = {"correct", "wrong", "blank", "unclear"},
                    requiredMode = Schema.RequiredMode.REQUIRED)
            String result,

            @Schema(
                    description = "정답. result가 unclear면 null(판정 전이라 주지 않음)",
                    example = "beautiful",
                    nullable = true,
                    requiredMode = Schema.RequiredMode.REQUIRED)
            String answer) {}
}
