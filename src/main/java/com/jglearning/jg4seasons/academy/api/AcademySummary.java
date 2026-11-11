package com.jglearning.jg4seasons.academy.api;

import java.time.ZoneId;

/** 운영자 화면의 학원 한 줄. status는 active·suspended, licenseEnforced = 교재 사용권 적용 여부(false = 직영, 모든 권 무제한). */
public record AcademySummary(
        long id, String name, String slug, String status, ZoneId timezone, boolean licenseEnforced) {}
