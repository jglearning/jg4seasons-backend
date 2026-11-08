package com.jglearning.jg4seasons.words.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 2.4 회차 단어(S-06·S-06b). 지문 순서 → 단어 순서. */
public record LessonWordsResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<WordItem> items,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int total) {

    public record WordItem(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            long id,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "회차 안 순서(1부터)")
            int order,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "지문 번호 1~6")
            int passageNo,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "sudden")
            String english,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "갑작스러운")
            String korean,

            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    allowableValues = {"n", "v", "adj", "adv", "prep", "conj", "phr"},
                    description = "품사. 화면 표기(명사·동사…)는 프론트")
            String pos,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "지문 문장이 아닌 새 예문(ADR-25)")
            String example,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String exampleKo,

            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    nullable = true,
                    description = "단어 발음. 음원이 아직 없으면 null")
            WordAudio audio,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "예문 음원. 없으면 null")
            WordAudio exampleAudio) {}

    public record WordAudio(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            long trackId,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "같은 도메인 상대 경로")
            String audioUrl,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "길이 ms")
            int durationMs) {}
}
