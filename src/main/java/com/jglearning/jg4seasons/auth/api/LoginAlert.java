package com.jglearning.jg4seasons.auth.api;

import java.time.Instant;

/**
 * 반 전체 로그인 실패 경고(AUTH-07, 교사 반 목록 4.1 loginAlert). failures = 최근 1시간 실패 수, since = 그중 가장 오래된 실패 시각(UTC). 반마다 최근
 * 1,000건까지만 기억한다 — 넘으면 failures는 1,000, since는 남은 기록 중 가장 오래된 시각.
 */
public record LoginAlert(int failures, Instant since) {}
