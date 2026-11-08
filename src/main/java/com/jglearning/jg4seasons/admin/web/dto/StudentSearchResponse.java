package com.jglearning.jg4seasons.admin.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 5.2 학원 전체 학생 검색 결과(퇴원 포함). */
public record StudentSearchResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<StudentSearchItem> items) {

    public record StudentSearchItem(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            long id,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String name,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
            String school,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
            String grade,

            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    allowableValues = {"active", "inactive"})
            String status,

            @JsonProperty("class")
            @Schema(
                    name = "class",
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    nullable = true,
                    description = "지금 반. 없으면(퇴원) null")
            StudentSearchClass clazz) {}

    public record StudentSearchClass(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            long id,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String name) {}
}
