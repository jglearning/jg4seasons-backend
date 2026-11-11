package com.jglearning.jg4seasons.system.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/** 권 사용권 한 줄과 사용 현황(7.3·7.4). */
public record LicenseItem(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BookRef book,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate startDate,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate endDate,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "학생 수 한도")
        int seats,

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "그 권 진행 중 반에 지금 소속된 서로 다른 학생 수(등록·이동 409와 같은 숫자). 한도를 낮추면 seats보다 클 수 있다")
        int usedSeats,

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"before_start", "active", "expired"},
                description = "학원 날짜 기준. licenseEnforced가 false인 학원은 늘 active")
        String status,

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                nullable = true,
                description = "active일 때 종료일 − 오늘 + 1(마지막 날 1). 그 밖·적용 안 하는 학원은 null")
        Integer daysLeft) {

    @Schema(name = "LicenseBookRef")
    public record BookRef(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String code,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "예: Book 1")
            String label,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "겨울·봄·여름·가을(블랙은 새봄)")
            String season) {}
}
