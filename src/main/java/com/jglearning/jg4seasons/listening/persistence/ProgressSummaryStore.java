package com.jglearning.jg4seasons.listening.persistence;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Objects;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 학원 현황(5.4)용 청취 집계 요약. track_progress·lesson_progress에는 academy_id가 없어서 학원 범위는 studentIds로만 정해진다 — 호출자가 그 학원 학생
 * id만 넘긴다(다른 모듈 테이블과 조인하지 않음). 빈 목록이면 쿼리를 보내지 않고 0. 시각은 UTC LocalDateTime으로 비교한다(JVM 시간대와 무관하게).
 */
@Component
public class ProgressSummaryStore {

    private final NamedParameterJdbcTemplate jdbc;

    public ProgressSummaryStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int completedCount(long lessonId, Collection<Long> studentIds) {
        if (studentIds.isEmpty()) {
            return 0;
        }
        return count(
                "select count(*) from lesson_progress"
                        + " where lesson_id = :lessonId and status = 'completed' and student_id in (:studentIds)",
                new MapSqlParameterSource("lessonId", lessonId).addValue("studentIds", studentIds));
    }

    /** last_played_at ∈ [from, to)인 서로 다른 학생 수 */
    public int playedBetween(Collection<Long> studentIds, Instant from, Instant to) {
        if (studentIds.isEmpty()) {
            return 0;
        }
        return count(
                "select count(distinct student_id) from track_progress"
                        + " where student_id in (:studentIds) and last_played_at >= :from and last_played_at < :to",
                new MapSqlParameterSource("studentIds", studentIds)
                        .addValue("from", LocalDateTime.ofInstant(from, ZoneOffset.UTC))
                        .addValue("to", LocalDateTime.ofInstant(to, ZoneOffset.UTC)));
    }

    private int count(String sql, MapSqlParameterSource params) {
        return Objects.requireNonNull(jdbc.queryForObject(sql, params, Integer.class));
    }
}
