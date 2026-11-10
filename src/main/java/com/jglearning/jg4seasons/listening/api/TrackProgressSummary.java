package com.jglearning.jg4seasons.listening.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * 학생 한 명의 지문 트랙 집계(track_progress). coverage 0~1, completed = 완료 판정됨. totalPlayMs(반복 포함)·sessionCount는 정직성 보조 지표(TRK-05).
 * lastPlayedAt은 UTC.
 */
public record TrackProgressSummary(
        BigDecimal coverage, boolean completed, long totalPlayMs, int sessionCount, Instant lastPlayedAt) {
    public TrackProgressSummary {
        Objects.requireNonNull(coverage, "coverage");
        Objects.requireNonNull(lastPlayedAt, "lastPlayedAt");
    }
}
