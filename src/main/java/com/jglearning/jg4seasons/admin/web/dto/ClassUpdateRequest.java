package com.jglearning.jg4seasons.admin.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 5.1 반 변경. 빠진 칸(null)은 바꾸지 않는다. 권·개강일·요일은 없음(새 반으로). */
public record ClassUpdateRequest(
        @Schema(description = "앞뒤 공백을 뺀 1~50자") @Size(max = 200)
        String name,

        @Schema(
                allowableValues = {"active", "finished", "archived"},
                description = "active가 아니면 그 반 가입 코드로 입장할 수 없음. 세 값 사이 자유롭게 바꿀 수 있음")
        @Pattern(regexp = "^(active|finished|archived)$", message = "상태는 active·finished·archived예요")
        String status,

        @Schema(description = "미래 회차 미리 듣기 허용") Boolean allowFutureLessons,

        @Schema(
                description =
                        "담당 교사 id 전체(교체). 빈 배열이면 모두 해제. 새로 넣는 교사는 그 학원의 활성 교사·관리자만(아니면 404), 이미 이 반 담당인 교사는 비활성이어도 그대로 둘 수 있음")
        List<@NotNull Long> teacherIds) {}
