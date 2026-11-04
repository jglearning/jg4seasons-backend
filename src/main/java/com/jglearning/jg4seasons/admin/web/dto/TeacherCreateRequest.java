package com.jglearning.jg4seasons.admin.web.dto;

import com.jglearning.jg4seasons.auth.api.StaffAccounts;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 5.3 교사 생성. */
public record TeacherCreateRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "1~30자") @NotBlank @Size(max = 30)
        String name,

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "영문·숫자·.·_·- 3~30자. 대소문자 구분 없음(소문자로 저장), 전체 학원에서 고유")
        @NotNull
        @Pattern(regexp = StaffAccounts.LOGIN_ID_REQUEST_REGEXP, message = StaffAccounts.LOGIN_ID_MESSAGE)
        String loginId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "8자 이상, 72바이트 이하")
        @NotNull
        @Size(min = 8, max = 72, message = "비밀번호는 8~72자예요")
        String password,

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"teacher", "admin"})
        @NotNull
        @Pattern(regexp = "^(teacher|admin)$", message = "역할은 teacher 또는 admin이에요")
        String role,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "담당 반 id. 없으면 빈 배열") @NotNull
        List<@NotNull Long> classIds) {

    @Override
    public String toString() {
        return "TeacherCreateRequest[name=" + name + ", loginId=" + loginId + ", role=" + role + "]";
    }
}
