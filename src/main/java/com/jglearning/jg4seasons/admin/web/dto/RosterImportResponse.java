package com.jglearning.jg4seasons.admin.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 5.5 명단 가져오기 결과(ADR 0036). classes는 요청 순서, 반마다 이번에 만든 학생과 개인 코드 평문(이 응답에서만 — 카드 인쇄용, 다시 볼 수 없음).
 */
public record RosterImportResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "요청 순서")
        List<ImportedClass> classes,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "이번에 만든 반 수")
        int createdClasses,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "이번에 만든 학생 수")
        int createdStudents) {

    public record ImportedClass(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            long classId,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "true = 이번에 만든 반, false = 기존 반")
            boolean created,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String name,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "반 가입 코드(카드 인쇄용)")
            String accessCode,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "L2")
            String levelCode,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            ClassResponse.ClassLevel level,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "5.1 반 응답의 season과 같음")
            String season,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "이번에 만든 학생, 요청 순서")
            List<PersonalCodeItem> students) {}
}
