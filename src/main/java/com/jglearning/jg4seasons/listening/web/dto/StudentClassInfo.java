package com.jglearning.jg4seasons.listening.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 학생 화면의 반 정보(2.1 홈·2.2 회차 목록 같은 모양). */
@Schema(name = "StudentClassInfo")
public record StudentClassInfo(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long id,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "겨울 그린 A반")
        String name,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Level level,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Book book,

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"before_start", "active", "expired"},
                description = "그 반 권의 사용 상태(ADR 0031). before_start = 사용 시작 전, expired = 사용 기간이 끝났거나 사용권 없음."
                        + " active가 아니면 그 반 회차를 열 때 403 LICENSE_INACTIVE. 사용권을 적용하지 않는 학원은 항상 active")
        String licenseStatus) {

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
                    allowableValues = {"겨울", "봄", "여름", "가을", "새봄"})
            String season,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Book 1", description = "머리글 표시 이름")
            String label) {}
}
