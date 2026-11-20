package com.jglearning.jg4seasons.listening.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/** 3.1 응답. sendBeacon은 응답을 못 받지만 상관없다(다음 회차 상세에서 집계를 다시 읽음). */
public record FlushResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Track track,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Passage passage,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Lesson lesson,

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "버린 세그먼트 수(범위·길이·빠진 칸·50개 초과). 재전송 중복은 세지 않음")
        int dropped) {

    @Schema(name = "FlushTrack")
    public record Track(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            long trackId,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "이 목소리 트랙만의 청취율 0~1")
            BigDecimal coverage,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "이 목소리 트랙만으로 완료 기준 도달(한 번 되면 유지)")
            boolean completed) {}

    /** 이 트랙의 지문. 목소리를 바꿔 들은 구간을 합친 값(ADR 0034) */
    @Schema(name = "FlushPassage")
    public record Passage(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "지문 번호")
            int no,

            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "지문 청취율 0~1. 여러 목소리로 들은 구간을 합친 값과 목소리 하나의 최댓값 중 큰 쪽")
            BigDecimal coverage,

            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "지문 완료. 목소리 하나를 기준 이상 들었거나 합친 청취율이 기준 이상(한 번 되면 유지)")
            boolean completed) {}

    @Schema(name = "FlushLesson")
    public record Lesson(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "L2-B1-05")
            String code,

            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    allowableValues = {"not_started", "in_progress", "completed"})
            String status,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "완료한 지문 수(목소리를 섞어 들어 완료한 지문 포함)")
            int requiredDone,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "지문 수")
            int requiredTotal) {}
}
