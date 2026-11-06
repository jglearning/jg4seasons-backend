package com.jglearning.jg4seasons.listening.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/** 3.1 응답. sendBeacon은 응답을 못 받지만 상관없다(다음 회차 상세에서 집계를 다시 읽음). */
public record FlushResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Track track,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Lesson lesson,

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "버린 세그먼트 수(범위·길이·빠진 칸·50개 초과). 재전송 중복은 세지 않음")
        int dropped) {

    @Schema(name = "FlushTrack")
    public record Track(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            long trackId,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "청취율 0~1")
            BigDecimal coverage,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "완료 기준 도달(한 번 되면 유지)")
            boolean completed) {}

    @Schema(name = "FlushLesson")
    public record Lesson(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "L2-B1-05")
            String code,

            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    allowableValues = {"not_started", "in_progress", "completed"})
            String status,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "완료한 지문 수")
            int requiredDone,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "지문 수")
            int requiredTotal) {}
}
