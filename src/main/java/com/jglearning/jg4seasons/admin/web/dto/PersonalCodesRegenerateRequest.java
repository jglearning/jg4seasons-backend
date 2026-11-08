package com.jglearning.jg4seasons.admin.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 5.2 개인 코드 여러 명 재발급. */
public record PersonalCodesRegenerateRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "그 반의 학생 id 1~50개")
        @NotNull
        @Size(min = 1, max = 50, message = "학생은 1~50명이에요")
        List<@NotNull Long> studentIds) {}
