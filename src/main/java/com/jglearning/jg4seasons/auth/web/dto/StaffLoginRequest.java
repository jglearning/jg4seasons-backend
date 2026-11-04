package com.jglearning.jg4seasons.auth.web.dto;

import com.jglearning.jg4seasons.auth.LoginIds;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 1.4 교사·관리자 로그인. */
public record StaffLoginRequest(
        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "영문·숫자·.·_·- 3~30자. 대소문자 구분 없음, 앞뒤 공백은 서버가 정리",
                example = "teacher01")
        @NotNull
        @Pattern(regexp = LoginIds.REQUEST_REGEXP, message = LoginIds.MESSAGE)
        String loginId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "8~72자")
        @NotNull
        @Size(min = 8, max = 72, message = "비밀번호는 8~72자예요")
        String password) {

    /** 비밀번호가 로그·예외 메시지에 찍히지 않게 */
    @Override
    public String toString() {
        return "StaffLoginRequest[loginId=" + loginId + "]";
    }
}
