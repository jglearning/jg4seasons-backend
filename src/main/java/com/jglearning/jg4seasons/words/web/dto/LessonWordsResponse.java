package com.jglearning.jg4seasons.words.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 2.4 회차 단어(S-06·S-06b). 교재 순서: 어휘책 그 강의 단어 → 숙어 → 어휘책에 없는 회차 어휘(ADR 0027 결정 6, 0029). */
public record LessonWordsResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<WordItem> items,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "회차 단어 수(실제 35~106, 없으면 0)")
        int total) {

    public record WordItem(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            long id,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "회차 안 교재 순서(1부터)")
            int order,

            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    allowableValues = {"book", "extra"},
                    description = "출처(ADR 0029): book 어휘책 그 강의 단어·숙어, extra 어휘책에 없는 회차 어휘. 목록 탭 필터용."
                            + " 음원 유무와는 따로(어휘책 음원이 들어오면 book도 음원이 있다)")
            String source,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "sudden")
            String english,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "갑작스러운")
            String korean,

            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    allowableValues = {"n", "v", "adj", "adv", "prep", "conj", "pron", "interj", "phr"},
                    description = "품사. 화면 표기(명사·동사·대명사·감탄사…)는 프론트")
            String pos,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "예문(지문 문장이 아님, ADR-25)")
            String example,

            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    nullable = true,
                    description = "예문 해석. 없으면 null(어휘책에만 있는 단어)")
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
