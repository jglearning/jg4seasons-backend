package com.jglearning.jg4seasons.teacher.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/** D-6 제출 상세. graded가 아니면 score·gradedAt은 null이고 items 칸은 아예 없다 */
public record TeacherWordTestDetail(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long id,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "L2-B1-01")
        String lessonCode,

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"A", "B", "C", "D"})
        String section,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "그 학생·회차·영역의 몇 번째 제출(1부터)")
        int attemptNo,

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

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "제출 시각(UTC)")
        Instant submittedAt,

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                nullable = true,
                description = "채점 끝난 시각(UTC). graded가 아니면 null")
        Instant gradedAt,

        @Schema(
                description = "문항별 결과, 번호 순. graded일 때만 있다(그 밖에는 칸 자체가 없음)",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @JsonInclude(JsonInclude.Include.NON_NULL)
        List<TeacherWordTestItem> items,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) TeacherWordTestStudentRef student,

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                nullable = true,
                description =
                        "답안지 사진 보기 서명 주소(5분). 쿠키 없이 바로 연다. 사진 키가 없거나 저장소가 보기 주소를 주지 않으면(로컬 서버) null. 서명 실패는 null이 아니라 500")
        String imageUrl) {}
