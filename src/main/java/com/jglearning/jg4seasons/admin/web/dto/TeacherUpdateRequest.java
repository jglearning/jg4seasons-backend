package com.jglearning.jg4seasons.admin.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 5.3 교사 변경. 빠진 칸(null)은 바꾸지 않는다. */
public record TeacherUpdateRequest(
        @Schema(description = "1~30자") @Size(min = 1, max = 30)
        String name,

        @Schema(
                allowableValues = {"active", "inactive"},
                description = "inactive면 로그인·refresh 불가, 그 교사의 refresh를 모두 끊음")
        @Pattern(regexp = "^(active|inactive)$", message = "상태는 active 또는 inactive예요")
        String status,

        @Schema(description = "담당 반 id 전체(교체). 빈 배열이면 모두 해제")
        List<Long> classIds,

        @Schema(description = "비밀번호 재설정(8자 이상, 72바이트 이하). 잠금이 풀리고 그 교사의 refresh를 모두 끊음")
        @Size(min = 8, max = 72, message = "비밀번호는 8~72자예요")
        String password) {

    @Override
    public String toString() {
        return "TeacherUpdateRequest[name=" + name + ", status=" + status + ", classIds=" + classIds + "]";
    }
}
