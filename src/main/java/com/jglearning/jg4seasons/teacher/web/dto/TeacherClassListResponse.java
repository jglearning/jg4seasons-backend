package com.jglearning.jg4seasons.teacher.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** 4.1 담당 반 목록(T-02). 관리자는 학원의 진행 중인 반 전체. */
public record TeacherClassListResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<TeacherClassItem> items) {

    public record TeacherClassItem(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            long id,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String name,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            TeacherClassLevel level,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            TeacherClassBook book,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "현재 소속 학생 수")
            int studentCount,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "오늘 회차. 개강 전이면 null")
            TeacherClassCurrent current,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "밀린 수업이 하나라도 있는 학생 수(ADR-24)")
            int overdueStudents,

            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    nullable = true,
                    description = "반 전체 개인 코드 실패가 1시간 20회를 넘으면, 아니면 null")
            LoginAlert loginAlert,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "다음 수업. 진도표가 끝났으면 null")
            TeacherClassNext next) {}

    public record TeacherClassCurrent(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "진도표 행 번호")
            int seq,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            TeacherLessonRef lesson,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            LocalDate scheduledDate,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "오늘 회차 완료 학생 수")
            int completed,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "진행 중 학생 수")
            int inProgress,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "시작 안 한 학생 수")
            int notStarted) {}

    public record TeacherClassNext(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            int seq,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            LocalDate scheduledDate) {}

    public record LoginAlert(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "최근 1시간 실패 수")
            int failures,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "세기 시작한 시각(UTC)")
            Instant since) {}
}
