package com.jglearning.jg4seasons.auth.api;

import java.time.Instant;

/** 반 전체 로그인 실패 경고(AUTH-07, 교사 반 목록 4.1 loginAlert). failures = 최근 1시간 실패 수, since = 그중 가장 오래된 실패 시각(UTC). */
public record LoginAlert(int failures, Instant since) {}
