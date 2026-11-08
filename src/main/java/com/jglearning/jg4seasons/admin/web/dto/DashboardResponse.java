package com.jglearning.jg4seasons.admin.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/** 5.4 학원 전체 현황(A-04). */
public record DashboardResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "진행 중(active) 반")
        List<DashboardClass> classes,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "오늘(학원 날짜) 한 번이라도 들은 학생 수")
        int activeStudentsToday) {

    public record DashboardClass(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            long id,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String name,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "오늘 회차. 개강 전이면 null")
            DashboardCurrent current,

            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    minimum = "0",
                    maximum = "1",
                    description = "오늘 회차를 완료한 학생 비율 0~1. current가 null이거나 학생이 0명이면 0")
            double completedRate) {}

    public record DashboardCurrent(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            int seq,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            DashboardLesson lesson,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            LocalDate scheduledDate) {}

    public record DashboardLesson(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "L2-B1-05")
            String code,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Reading Day 5")
            String title) {}
}
