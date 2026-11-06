package com.jglearning.jg4seasons.listening.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 학생 화면의 반 정보(2.1 홈·2.2 회차 목록 같은 모양). */
@Schema(name = "StudentClassInfo")
public record StudentClassInfo(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long id,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "겨울 그린 A반")
        String name,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Level level,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Book book) {

    @Schema(name = "StudentClassLevel")
    public record Level(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "L2")
            String code,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "그린")
            String nameKo,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "#1F7A4F")
            String colorHex) {}

    @Schema(name = "StudentClassBook")
    public record Book(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "L2-B1")
            String code,

            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    allowableValues = {"겨울", "봄", "여름", "가을"})
            String season,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Book 1", description = "머리글 표시 이름")
            String label) {}
}
