package com.jglearning.jg4seasons.academy.api;

import org.jspecify.annotations.Nullable;

/** 사용권 지정 결과(7.4). before는 처음 만든 경우 null — 감사 기록의 전·후 값. */
public record LicenseWrite(long licenseId, @Nullable LicenseRow before, LicenseRow after) {}
