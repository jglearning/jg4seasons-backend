package com.jglearning.jg4seasons.listening.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * 학생 한 명의 지문 트랙 집계(track_progress). coverage 0~1, completed = 완료 판정됨. totalPlayMs(반복 포함)·sessionCount는 정직성 보조 지표(TRK-05).
 * lastPlayedAt은 UTC. 범위를 벗어난 값은 데이터 오류(IllegalStateException → 500) — 교사 화면에 그대로 나가지 않게 여기서 막는다.
 */
public record TrackProgressSummary(
        BigDecimal coverage, boolean completed, long totalPlayMs, int sessionCount, Instant lastPlayedAt) {
    public TrackProgressSummary {
        Objects.requireNonNull(lastPlayedAt, "lastPlayedAt");
        if (coverage == null || coverage.signum() < 0 || coverage.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalStateException("청취율은 0~1이어야 한다: " + coverage);
        }
        if (totalPlayMs < 0 || sessionCount < 0) {
            throw new IllegalStateException("재생 시간·횟수는 0 이상이어야 한다: " + totalPlayMs + ", " + sessionCount);
        }
    }
}
