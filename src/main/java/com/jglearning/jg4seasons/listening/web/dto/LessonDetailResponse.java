package com.jglearning.jg4seasons.listening.web.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

/** 2.3 회차 상세(S-05). 순서는 화면 순서(지문 no, 문장 no, 속도). */
public record LessonDetailResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Lesson lesson,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Level level,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Completion completion,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Passage> passages,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Words words) {

    @Schema(name = "LessonDetailLesson")
    public record Lesson(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "L2-B1-05")
            String code,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            int number,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Reading Day 5")
            String title,

            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    allowableValues = {"regular", "module_review", "integrated"})
            String type) {}

    /** 반 레벨(진행 바·완료 표시 색) */
    @Schema(name = "LessonDetailLevel")
    public record Level(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "L2")
            String code,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "그린")
            String nameKo,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "#1F7A4F")
            String colorHex) {}

    @Schema(name = "LessonDetailCompletion")
    public record Completion(
            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    allowableValues = {"not_started", "in_progress", "completed"})
            String status,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            int passagesDone,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            int passagesTotal,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "트랙 완료 기준 청취율(0~1)")
            BigDecimal threshold) {}

    @Schema(name = "LessonDetailPassage")
    public record Passage(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            int no,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String title,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            int wordCount,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            List<Sentence> sentences,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            List<Speed> speeds) {}

    @Schema(name = "LessonDetailSentence")
    public record Sentence(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            int no,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String text,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "대화문 화자. 대화문이 아니면 null")
            String speaker) {}

    @Schema(name = "LessonDetailSpeed")
    public record Speed(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            long trackId,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1.0", description = "속도(0.8·1.0·1.2)")
            String variant,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "같은 도메인 상대 경로")
            String audioUrl,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "음원 길이(ms)")
            int durationMs,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "지문 완료에 필요한 속도인가(완료 규칙 결과)")
            boolean required,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "청취율 0~1(병합된 구간 / 길이)")
            BigDecimal coverage,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            boolean completed,

            @ArraySchema(
                    arraySchema =
                            @Schema(
                                    requiredMode = Schema.RequiredMode.REQUIRED,
                                    description = "timings[i] = sentences[i]의 [시작, 끝] ms. 문장 시각이 아직 없으면 빈 배열"),
                    schema = @Schema(type = "array", example = "[0, 3000]"))
            List<int[]> timings,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "true면 1.0 시각을 길이 비율로 늘이거나 줄인 근사")
            boolean timingsApproximate,

            @ArraySchema(
                    arraySchema =
                            @Schema(
                                    requiredMode = Schema.RequiredMode.REQUIRED,
                                    description = "병합된 들은 구간 [시작, 끝] ms. 들은 적 없으면 빈 배열"),
                    schema = @Schema(type = "array", example = "[0, 52000]"))
            List<int[]> heardRanges) {}

    @Schema(name = "LessonDetailWords")
    public record Words(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "회차 단어 수")
            int total,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "암기 모드 '알아요' 최고 수")
            int memKnown) {}
}
