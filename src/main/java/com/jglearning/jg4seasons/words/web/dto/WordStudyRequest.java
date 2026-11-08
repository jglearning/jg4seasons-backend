package com.jglearning.jg4seasons.words.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 2.5 카드·학습 결과. 보낼 칸만(빠진 칸은 그대로), 하나도 없으면 400. 값은 단조 증가로 저장 — 저장된 값보다 작으면 무시. 상한 127(word_study 칸 크기).
 */
public record WordStudyRequest(
        @Schema(description = "본 카드 수") @Min(0) @Max(127) Integer cardsViewed,

        @Schema(description = "암기 모드 '알아요' 수") @Min(0) @Max(127)
        Integer memKnown,

        @Schema(description = "리콜 모드 맞힌 수(최고 기록으로 저장)") @Min(0) @Max(127)
        Integer recallCorrect,

        @Schema(description = "스펠 모드 맞힌 수(최고 기록으로 저장)") @Min(0) @Max(127)
        Integer spellCorrect) {

    /** 바꿀 값이 하나라도 있는가 */
    public boolean hasAny() {
        return cardsViewed != null || memKnown != null || recallCorrect != null || spellCorrect != null;
    }
}
