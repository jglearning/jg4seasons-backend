package com.jglearning.jg4seasons.auth.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** 1.1 가입 코드로 반 확인. */
public record CodeVerifyRequest(
        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "가입 코드(영숫자 8자). 소문자·앞뒤 공백은 서버가 정리한다",
                example = "G7KM2XP4")
        @NotNull
        @Pattern(regexp = AccessCodeFormat.REGEXP, message = AccessCodeFormat.MESSAGE)
        String code) {}
