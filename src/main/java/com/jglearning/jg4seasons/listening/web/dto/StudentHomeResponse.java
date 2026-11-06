package com.jglearning.jg4seasons.listening.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/** 2.1 학생 홈(S-03). 학생이 두 반이면 첫 active 반 기준, 나머지는 otherClasses. */
public record StudentHomeResponse(
        @JsonProperty("class") @Schema(name = "class", requiredMode = Schema.RequiredMode.REQUIRED)
        StudentClassInfo clazz,

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                nullable = true,
                description = "수업일이 지난 회차 중 가장 늦은 것. 개강 전이면 null")
        Today today,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "밀린 수업(ADR-24), 오래된 순. 없으면 빈 배열")
        List<Overdue> overdue,

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                nullable = true,
                description = "오늘 회차의 단어. 오늘 회차가 없으면 null")
        Words words,

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                nullable = true,
                description = "수업일이 오늘 뒤인 가장 이른 회차. 종강 뒤면 null")
        Next next,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Stats stats,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<OtherClass> otherClasses) {

    @Schema(name = "StudentHomeToday")
    public record Today(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            int seq,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            LocalDate scheduledDate,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "겨울 1", description = "시즌 이름 + 묶음 번호")
            String groupLabel,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            TodayLesson lesson,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            LessonProgressView progress) {}

    @Schema(name = "StudentHomeTodayLesson")
    public record TodayLesson(
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

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            List<PassageTitle> passages) {}

    @Schema(name = "StudentHomePassageTitle")
    public record PassageTitle(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            int no,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String title) {}

    @Schema(name = "StudentHomeLessonRef")
    public record LessonRef(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String code,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String title) {}

    @Schema(name = "StudentHomeOverdue")
    public record Overdue(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            int seq,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            LocalDate scheduledDate,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            LessonRef lesson,

            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    allowableValues = {"not_started", "stopped"},
                    description = "not_started 미참여 · stopped 멈춤")
            String kind,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            LessonProgressView progress) {}

    @Schema(name = "StudentHomeNext")
    public record Next(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            int seq,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            LocalDate scheduledDate,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            LessonRef lesson) {}

    @Schema(name = "StudentHomeWords")
    public record Words(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String lessonCode,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "회차 단어 수")
            int total,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "암기 모드 '알아요' 최고 수")
            int memKnown) {}

    @Schema(name = "StudentHomeStats")
    public record Stats(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            int completedLessons,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "overdue 배열 길이와 같다")
            int overdueLessons,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "진도표 회차 수")
            int totalLessons) {}

    @Schema(name = "StudentHomeOtherClass")
    public record OtherClass(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            long id,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String name) {}
}
