package com.jglearning.jg4seasons.admin.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/** 7.5 학원관리자 교재 사용권 보기(A-05). 항목 모양은 운영자 7.3의 licenses[]와 같다. */
public record LicenseListResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "교재 사용권 적용 여부. false = 직영(모든 권 무제한)")
        boolean licenseEnforced,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "학원 타임존의 오늘")
        LocalDate today,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "사용권 줄이 있는 권만, 레벨·권 순")
        List<Item> items) {

    @Schema(name = "AcademyLicenseItem")
    public record Item(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            BookRef book,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            LocalDate startDate,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            LocalDate endDate,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "학생 수 한도")
            int seats,

            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "그 권 진행 중 반에 지금 소속된 서로 다른 학생 수. 한도를 넘으면 그 권 반으로 새 등록·이동이 409")
            int usedSeats,

            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    allowableValues = {"before_start", "active", "expired"})
            String status,

            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    nullable = true,
                    description = "active일 때 종료일 − 오늘 + 1(마지막 날 1), 그 밖 null")
            Integer daysLeft) {}

    @Schema(name = "AcademyLicenseBookRef")
    public record BookRef(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String code,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String label,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String season) {}
}
