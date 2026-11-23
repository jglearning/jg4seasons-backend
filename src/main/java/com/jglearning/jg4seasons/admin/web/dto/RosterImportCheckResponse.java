package com.jglearning.jg4seasons.admin.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 5.5 명단 가져오기 검사 결과(ADR 0036). errors가 비어야 만들 수 있다. warnings는 만들기를 막지 않는다(확인은 화면). 둘 다 요청 순서(반 항목 순, 전체 학생 수
 * 오류만 맨 앞).
 */
public record RosterImportCheckResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "고쳐야 만들 수 있는 것. 비면 만들 수 있음")
        List<Item> errors,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "만들 수는 있지만 확인할 것")
        List<Item> warnings) {

    public record Item(
            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    description =
                            "요청 안 위치: classes · classes[1] · classes[1].newClass.bookCode · classes[1].students[3].name",
                    example = "classes[1].students[3].name")
            String field,

            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    description =
                            "오류: VALIDATION · NOT_FOUND · CLASS_NOT_ACTIVE · LICENSE_INACTIVE · LICENSE_SEATS_FULL (만들기가 실패할 때와 같은"
                                    + " code). 경고: SAME_NAME_STUDENT · SAME_NAME_CLASS")
            String code,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "화면에 그대로 보일 문구")
            String message) {}
}
