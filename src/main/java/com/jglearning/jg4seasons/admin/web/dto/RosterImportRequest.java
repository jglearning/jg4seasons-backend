package com.jglearning.jg4seasons.admin.web.dto;

import com.jglearning.jg4seasons.admin.web.dto.StudentCreateRequest.StudentCreateItem;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * 5.5 명단 가져오기(ADR 0036). 검사(`/check`)와 만들기가 같은 본문을 받는다. 반 항목마다 기존 반 id와 새 반 중 하나. 전체 학생 500명 한도는 검사
 * 규칙이 본다(반 30개 × 50명보다 작음).
 */
public record RosterImportRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "반 항목 1~30개, 요청 순서대로 만든다")
        @NotNull
        @Size(min = 1, max = 30, message = "반은 1~30개예요")
        List<@NotNull @Valid Item> classes) {

    @Schema(name = "RosterImportClassItem", description = "반 항목 하나: 기존 반 id 또는 새 반, 그리고 학생")
    public record Item(
            @Schema(description = "학생을 넣을 기존 반 id. newClass와 둘 중 하나만") @Nullable
            Long existingClassId,

            @Schema(description = "새로 만들 반(5.1 반 만들기와 같은 칸). existingClassId와 둘 중 하나만") @Nullable @Valid
            ClassCreateRequest newClass,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "1~50명. 새 학생으로 만든다(기존 학생을 찾아 옮기지 않음)")
            @NotNull
            @Size(min = 1, max = 50, message = "학생은 1~50명이에요")
            List<@NotNull @Valid StudentCreateItem> students) {}
}
