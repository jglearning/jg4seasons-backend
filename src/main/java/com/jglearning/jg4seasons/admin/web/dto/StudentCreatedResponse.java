package com.jglearning.jg4seasons.admin.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 5.2 학생 등록 결과(요청 순서). 카드 인쇄용 개인 코드 평문은 이 응답에서만. */
public record StudentCreatedResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<PersonalCodeItem> created) {}
