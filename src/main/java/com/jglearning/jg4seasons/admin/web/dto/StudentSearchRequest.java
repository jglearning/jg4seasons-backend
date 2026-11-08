package com.jglearning.jg4seasons.admin.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 5.2 이름으로 학생 찾기. 이름을 쿼리 문자열(주소·로그)에 싣지 않으려고 본문으로 받는다. */
public record StudentSearchRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "이름 일부, 1~30자")
        @NotBlank
        @Size(min = 1, max = 30)
        String name) {}
