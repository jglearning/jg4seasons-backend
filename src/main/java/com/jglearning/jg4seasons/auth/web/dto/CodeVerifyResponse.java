package com.jglearning.jg4seasons.auth.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/** 1.1 응답. S-01에서 8자를 다 쳤을 때 "반 이름 · 학원 이름"을 보여 주는 용도. 학생 명단은 없다. */
public record CodeVerifyResponse(
        @JsonProperty("class") @Schema(name = "class", requiredMode = Schema.RequiredMode.REQUIRED)
        ClassInfo clazz) {

    public record ClassInfo(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            long id,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "겨울 그린 A반")
            String name,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String academyName,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            LevelInfo level,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            BookInfo book) {}

    public record LevelInfo(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "L2")
            String code,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "그린")
            String nameKo,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "#RRGGBB", example = "#1F7A4F")
            String colorHex) {}

    public record BookInfo(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "L2-B1")
            String code,

            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    allowableValues = {"겨울", "봄", "여름", "가을", "새봄"})
            String season) {}
}
