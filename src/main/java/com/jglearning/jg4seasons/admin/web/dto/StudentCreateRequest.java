package com.jglearning.jg4seasons.admin.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 5.2 학생 등록. 한 명도 배열로. 학생 개인정보는 이름·학교·학년만. */
public record StudentCreateRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "1~50명")
        @NotNull
        @Size(min = 1, max = 50, message = "학생은 1~50명이에요")
        List<@NotNull @Valid StudentCreateItem> students) {

    public record StudentCreateItem(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "1~30자")
            @NotBlank
            @Size(min = 1, max = 30)
            String name,

            @Schema(description = "50자 이하") @Size(max = 50) String school,

            @Schema(description = "10자 이하(예: 중1)") @Size(max = 10)
            String grade) {}
}
