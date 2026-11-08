package com.jglearning.jg4seasons.words.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 2.5 저장된 학습 결과(누적·최고 기록). */
public record WordStudyResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int cardsViewed,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "회차 단어를 모두 봤는가")
        boolean allViewed,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int memKnown,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int recallBest,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int spellBest) {}
