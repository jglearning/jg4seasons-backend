package com.jglearning.jg4seasons.system.web.dto;

import com.jglearning.jg4seasons.academy.api.AcademyRegistry;
import com.jglearning.jg4seasons.auth.api.StaffAccounts;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 7.2 학원 + 첫 관리자. */
public record AcademyCreateRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "1~50자") @NotBlank @Size(max = 50)
        String name,

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "학원 주소. 소문자·숫자·- 2~30자, 영숫자로 시작, 전체에서 고유. demo는 쓸 수 없다")
        @NotNull
        @Pattern(regexp = AcademyRegistry.SLUG_REGEXP, message = AcademyRegistry.SLUG_MESSAGE)
        String slug,

        @Schema(description = "IANA 타임존 이름(예: Asia/Seoul). 없으면 Asia/Seoul. 오프셋(+09:00)은 받지 않는다", nullable = true)
        String timezone,

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "교재 사용권 적용 여부. 직영 false(모든 권 무제한), 연계 학원 true(사용권이 있는 권만)")
        @NotNull
        Boolean licenseEnforced,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Valid
        Admin admin) {

    /** 첫 관리자 계정. 규칙은 5.3 교사 생성과 같다 */
    @Schema(name = "AcademyAdminRequest")
    public record Admin(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "영문·숫자·.·_·- 3~30자. 소문자로 저장, 전체에서 고유")
            @NotNull
            @Pattern(regexp = StaffAccounts.LOGIN_ID_REQUEST_REGEXP, message = StaffAccounts.LOGIN_ID_MESSAGE)
            String loginId,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "8자 이상, 72바이트 이하")
            @NotNull
            @Size(min = 8, max = 72, message = "비밀번호는 8~72자예요")
            String password,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "1~30자") @NotBlank @Size(max = 30)
            String name) {

        @Override
        public String toString() {
            return "Admin[loginId=" + loginId + ", name=" + name + "]";
        }
    }
}
