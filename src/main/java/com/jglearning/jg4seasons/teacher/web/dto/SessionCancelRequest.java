package com.jglearning.jg4seasons.teacher.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 4.7 휴강(ADR 0030). 그 행은 날짜 그대로 cancelled로 남고, shiftFollowing이면 같은 회차가 새 행으로 다음 수업일에 들어가 뒤 수업이 한 칸씩 밀린다(행 +1). */
public record SessionCancelRequest(
        @Schema(description = "메모 100자 이하") @Size(max = 100) String note,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "뒤 수업을 밀지 여부") @NotNull
        Boolean shiftFollowing) {}
