package com.jglearning.jg4seasons.teacher.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/** 4.3 교사용 학생 상세(T-04). 열린 회차만, seq 순. */
public record StudentDetailResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) StudentDetailStudent student,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "그 반에서 열린 회차(seq 오름차순)")
        List<StudentDetailLesson> lessons) {

    public record StudentDetailStudent(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            long id,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String name,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
            String school,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
            String grade,

            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    nullable = true,
                    description = "마지막 로그인(UTC), 없으면 null")
            Instant lastLoginAt) {}

    public record StudentDetailLesson(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "L2-B1-05")
            String code,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Reading Day 5")
            String title,

            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    allowableValues = {"not_started", "in_progress", "completed"})
            String status,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            int requiredDone,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            int requiredTotal,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "지문 트랙(선택 속도 포함)")
            List<StudentDetailTrack> tracks,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            StudentDetailWords words) {}

    public record StudentDetailTrack(
            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    allowableValues = {"passage"})
            String kind,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "지문 번호 1~6")
            int passageNo,

            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    allowableValues = {"0.8", "1.0", "1.2"})
            String variant,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "회차 완료에 필요한 트랙인가")
            boolean required,

            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    minimum = "0",
                    maximum = "1",
                    description = "들은 비율 0~1")
            double coverage,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            boolean completed,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "반복 포함 총 재생 ms")
            long totalPlayMs,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "트랙 길이 ms")
            int durationMs,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            int sessionCount,

            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    nullable = true,
                    description = "마지막 재생(UTC), 안 들었으면 null")
            Instant lastPlayedAt) {}

    public record StudentDetailWords(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            int cardsViewed,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            int memKnown,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            int recallBest,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            int spellBest) {}
}
