package com.jglearning.jg4seasons.teacher.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** 4.2 청취 현황 매트릭스(T-03). 행 = 학생, 열 = 회차(휴강 행은 오지 않음). */
public record MatrixResponse(
        @JsonProperty("class") @Schema(name = "class", requiredMode = Schema.RequiredMode.REQUIRED)
        MatrixClass clazz,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "요청 범위의 실제 수업(휴강 제외), seq 순")
        List<MatrixSession> sessions,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "현재 소속 학생(이름 순)")
        List<MatrixStudent> students,

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description =
                        "학생 id(문자열) → 회차 코드 → 칸. 열린 회차(opened)는 현재 학생마다 칸이 항상 있다(안 들었어도 not_started 칸 — 밀림 표시 overdue를 싣는다). 열리지 않은 회차는 칸이 없다(수업 전)")
        Map<String, Map<String, MatrixCell>> cells) {

    public record MatrixClass(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            long id,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String name,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            TeacherClassLevel level) {}

    public record MatrixSession(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "진도표 행 번호(휴강 행도 번호를 차지한다)")
            int seq,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "열 머리글은 lesson.number")
            MatrixLesson lesson,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            LocalDate scheduledDate,

            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    allowableValues = {"scheduled", "done"})
            String status,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "오늘 회차인가")
            boolean isCurrent,

            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "회차가 열렸는가(학원 날짜·allowFutureLessons 포함 — 학생 화면과 같은 규칙). false면 '수업 전'")
            boolean opened) {}

    public record MatrixLesson(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "L2-B1-05")
            String code,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "회차 번호 1~24")
            int number,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Reading Day 5")
            String title) {}

    public record MatrixStudent(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            long id,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String name,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
            String school,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
            String grade) {}

    public record MatrixCell(
            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    allowableValues = {"not_started", "in_progress", "completed"})
            String status,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "완료한 필수 트랙 수")
            int requiredDone,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "필수 트랙 수")
            int requiredTotal,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "완료 시각(UTC), 미완료면 null")
            Instant completedAt,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "밀린 수업인가(ADR-24, 빨간 테두리)")
            boolean overdue) {}
}
