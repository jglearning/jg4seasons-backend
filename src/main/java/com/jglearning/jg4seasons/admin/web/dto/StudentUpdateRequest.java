package com.jglearning.jg4seasons.admin.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 5.2 학생 변경. 빠진 칸(null)은 바꾸지 않는다. 학교·학년은 빈 문자열이면 지운다. */
public record StudentUpdateRequest(
        @Schema(description = "1~30자")
        @Size(min = 1, max = 30)
        @Pattern(regexp = "(?s).*\\S.*", message = "이름을 입력해 주세요")
        String name,

        @Schema(description = "50자 이하. 빈 문자열이면 지움") @Size(max = 50)
        String school,

        @Schema(description = "10자 이하. 빈 문자열이면 지움") @Size(max = 10)
        String grade,

        @Schema(
                allowableValues = {"active", "inactive"},
                description = "inactive = 퇴원(기록 보존, 로그인 불가)")
        @Pattern(regexp = "^(active|inactive)$", message = "상태는 active 또는 inactive예요")
        String status) {

    /** 바꿀 값이 하나라도 있는가 */
    public boolean hasChange() {
        return name != null || school != null || grade != null || status != null;
    }
}
