package com.jglearning.jg4seasons.system.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/** 7.3 학원 상세·사용권 현황. */
public record AcademyDetailResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) AcademyResponse academy,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "그 학원 타임존의 오늘")
        LocalDate today,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "사용권 줄이 있는 권만, 레벨·권 순")
        List<LicenseItem> licenses) {}
