package com.jglearning.jg4seasons.admin.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 5.2 재발급 결과. 새 개인 코드 평문은 이 응답에서만. */
public record PersonalCodesResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<PersonalCodeItem> items) {}
