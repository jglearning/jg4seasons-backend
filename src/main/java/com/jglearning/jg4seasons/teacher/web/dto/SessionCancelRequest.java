package com.jglearning.jg4seasons.teacher.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 4.7 휴강. 그 행은 cancelled로 남고, shiftFollowing이면 그 회차부터 다음 수업일로 밀린다(24회 유지, 끝이 늘어남). */
public record SessionCancelRequest(
        @Schema(description = "메모 100자 이하") @Size(max = 100) String note,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "뒤 수업을 밀지 여부") @NotNull
        Boolean shiftFollowing) {}
