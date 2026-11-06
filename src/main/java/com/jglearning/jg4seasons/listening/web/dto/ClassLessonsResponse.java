package com.jglearning.jg4seasons.listening.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/** 2.2 회차 목록(S-04). 묶음(8회차씩)별, 회차는 진도표 순서. state는 서버가 정한다(프론트는 날짜 계산을 하지 않음). */
public record ClassLessonsResponse(
        @JsonProperty("class") @Schema(name = "class", requiredMode = Schema.RequiredMode.REQUIRED)
        StudentClassInfo clazz,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Group> groups) {

    @Schema(name = "ClassLessonsGroup")
    public record Group(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "묶음 번호 1~3")
            int module,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "겨울 1", description = "시즌 이름 + 묶음 번호")
            String label,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            Range range,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            List<Lesson> lessons) {}

    @Schema(name = "ClassLessonsRange")
    public record Range(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "묶음 첫 수업일")
            LocalDate from,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "묶음 마지막 수업일")
            LocalDate to) {}

    @Schema(name = "ClassLessonsLesson")
    public record Lesson(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "L2-B1-05")
            String code,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            int number,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String title,

            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    allowableValues = {"regular", "module_review", "integrated"})
            String type,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "진도표 수업일(휴강 뒤 옮겨졌으면 새 날짜)")
            LocalDate scheduledDate,

            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    allowableValues = {"completed", "today", "overdue", "open", "locked"},
                    description = "open = 수업일 전이지만 열림(미래 회차 허용 반), locked = 수업일 전 또는 휴강만 있음")
            String state,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            LessonProgressView progress) {}
}
