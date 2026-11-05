package com.jglearning.jg4seasons.admin.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** 관리자용 반 정보(목록·상세 같은 모양). 가입 코드는 관리자에게만 보인다. */
public record ClassResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "L2-B1")
        String bookCode,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "L2")
        String levelCode,

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"겨울", "봄", "여름", "가을"})
        String season,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "개강일(학원 날짜)")
        LocalDate startDate,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "수업 요일 1(월)~7(일), 오름차순")
        List<Integer> weekdays,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "가입 코드 8자")
        String accessCode,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "가입 코드 발급 시각(UTC, 초 단위)")
        Instant codeIssuedAt,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean allowFutureLessons,

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"active", "finished", "archived"})
        String status,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "담당 교사(id 오름차순)")
        List<ClassTeacher> teachers,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "현재 소속 학생 수")
        int studentCount) {

    public record ClassTeacher(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            long id,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String name) {}
}
