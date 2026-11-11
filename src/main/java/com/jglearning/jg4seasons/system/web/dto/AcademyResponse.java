package com.jglearning.jg4seasons.system.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 운영자 화면의 학원(7.1 목록 한 줄·7.3 상세 머리). */
public record AcademyResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String slug,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "IANA 타임존 — 진도표·회차 열림·사용권 기간의 \"오늘\" 기준")
        String timezone,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "교재 사용권 적용 여부. false = 직영(모든 권 무제한)")
        boolean licenseEnforced,

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"active", "suspended"})
        String status) {}
