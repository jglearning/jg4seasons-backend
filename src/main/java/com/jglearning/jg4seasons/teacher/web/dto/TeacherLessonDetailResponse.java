package com.jglearning.jg4seasons.teacher.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** 4.4 교사용 회차 상세(T-05): 학생 × 트랙 표. 학생 회차 상세(2.3 LessonDetailResponse)와 다른 응답. */
public record TeacherLessonDetailResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) TeacherLessonInfo lesson,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "지문 트랙(목소리별, required로 구분)")
        List<TeacherLessonTrack> tracks,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "현재 소속 학생(이름 순)")
        List<TeacherLessonRow> rows) {

    public record TeacherLessonInfo(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "L2-B1-05")
            String code,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Reading Day 5")
            String title,

            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    nullable = true,
                    description = "그 반 진도표의 수업일. 휴강만 있으면 null")
            LocalDate scheduledDate) {}

    public record TeacherLessonTrack(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            long trackId,

            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    allowableValues = {"passage"})
            String kind,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            int passageNo,

            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    allowableValues = {"0.8", "1.0", "1.2"})
            String variant,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            boolean required) {}

    public record TeacherLessonRow(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            TeacherLessonStudent student,

            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    allowableValues = {"not_started", "in_progress", "completed"})
            String status,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "트랙 id(문자열) → 결과. 없으면 안 들음")
            Map<String, TeacherLessonTrackResult> byTrack) {}

    public record TeacherLessonStudent(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            long id,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String name) {}

    public record TeacherLessonTrackResult(
            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    minimum = "0",
                    maximum = "1",
                    description = "들은 비율 0~1")
            double coverage,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            boolean completed) {}
}
