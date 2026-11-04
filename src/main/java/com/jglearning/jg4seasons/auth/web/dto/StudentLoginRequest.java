package com.jglearning.jg4seasons.auth.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** 1.2 가입 코드 + 개인 코드. */
public record StudentLoginRequest(
        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "가입 코드(영숫자 8자). 소문자·앞뒤 공백은 서버가 정리한다",
                example = "G7KM2XP4")
        @NotNull
        @Pattern(regexp = AccessCodeFormat.REGEXP, message = AccessCodeFormat.MESSAGE)
        String code,

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "개인 코드. 문자열 숫자 4자리, 0으로 시작할 수 있다",
                example = "0423")
        @NotNull
        @Pattern(regexp = "^[0-9]{4}$", message = "개인 코드는 숫자 4자리예요")
        String personalCode) {}
