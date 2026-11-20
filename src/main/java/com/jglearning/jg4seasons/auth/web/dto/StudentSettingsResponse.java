package com.jglearning.jg4seasons.auth.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 1.8·1.9 학생 설정(ADR 0035). 조회와 수정 응답이 같은 모양이고 늘 설정 전체다. */
public record StudentSettingsResponse(
        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                nullable = true,
                allowableValues = {"us_w", "us_m", "uk_w", "uk_m"},
                description = "고른 지문 목소리. null = 고른 적 없음(기본 목소리는 화면이 정한다)")
        String voice) {}
