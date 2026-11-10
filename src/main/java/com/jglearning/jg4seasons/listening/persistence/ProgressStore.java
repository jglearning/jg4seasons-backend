package com.jglearning.jg4seasons.listening.persistence;

import com.jglearning.jg4seasons.listening.api.LessonProgressStatus;
import com.jglearning.jg4seasons.listening.api.LessonProgressSummary;
import com.jglearning.jg4seasons.listening.api.TrackProgressSummary;
import com.jglearning.jg4seasons.listening.segment.Range;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 학생 청취 집계 읽기(ADR-06 — 화면은 집계만). track_progress·lesson_progress에는 academy_id가 없다: studentId는 JWT에서 온 값이고, 그 학생이 그
 * 학원 반 소속임을 호출자가 먼저 확인한 뒤에 부른다.
 */
@Component
public class ProgressStore {

    /** completed = completed_at이 있음(flush의 완료 판정 결과). coverage 0~1 — 벗어나면 데이터 오류 */
    public record TrackProgressRow(long trackId, BigDecimal coverage, boolean completed, List<Range> mergedRanges) {
        public TrackProgressRow {
            if (coverage == null || coverage.signum() < 0 || coverage.compareTo(BigDecimal.ONE) > 0) {
                throw new IllegalStateException("청취율은 0~1이어야 한다: track " + trackId + " " + coverage);
            }
            mergedRanges = List.copyOf(mergedRanges);
        }
    }

    public record LessonProgressRow(LessonProgressStatus status, int passagesDone, int passagesTotal) {}

    private final NamedParameterJdbcTemplate jdbc;
    private final JsonMapper jsonMapper;

    public ProgressStore(NamedParameterJdbcTemplate jdbc, JsonMapper jsonMapper) {
        this.jdbc = jdbc;
        this.jsonMapper = jsonMapper;
    }

    /** 집계가 있는 트랙만 담는다(없는 트랙 = 아직 안 들음) */
    public Map<Long, TrackProgressRow> trackProgress(long studentId, Collection<Long> trackIds) {
        if (trackIds.isEmpty()) {
            return Map.of();
        }
        List<TrackProgressRow> rows = jdbc.query(
                "select track_id, coverage, completed_at, merged_ranges from track_progress"
                        + " where student_id = :studentId and track_id in (:trackIds)",
                new MapSqlParameterSource("studentId", studentId).addValue("trackIds", trackIds),
                (rs, i) -> new TrackProgressRow(
                        rs.getLong("track_id"),
                        rs.getBigDecimal("coverage"),
                        rs.getObject("completed_at") != null,
                        RangesJson.read(jsonMapper, rs.getString("merged_ranges"))));
        return Collections.unmodifiableMap(
                rows.stream().collect(Collectors.toMap(TrackProgressRow::trackId, Function.identity())));
    }

    /** 여러 회차 진도를 한 번에(홈·회차 목록). 진도가 있는 회차만 담는다(없으면 시작 전) */
    public Map<Long, LessonProgressRow> lessonProgress(long studentId, Collection<Long> lessonIds) {
        if (lessonIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, LessonProgressRow> rows = new HashMap<>();
        jdbc.query(
                "select lesson_id, status, passages_done, passages_total from lesson_progress"
                        + " where student_id = :studentId and lesson_id in (:lessonIds)",
                new MapSqlParameterSource("studentId", studentId).addValue("lessonIds", lessonIds),
                rs -> {
                    rows.put(
                            rs.getLong("lesson_id"),
                            new LessonProgressRow(
                                    LessonProgressStatus.fromValue(rs.getString("status")),
                                    rs.getInt("passages_done"),
                                    rs.getInt("passages_total")));
                });
        return Collections.unmodifiableMap(rows);
    }

    public Optional<LessonProgressRow> lessonProgress(long studentId, long lessonId) {
        return jdbc
                .query(
                        "select status, passages_done, passages_total from lesson_progress"
                                + " where student_id = :studentId and lesson_id = :lessonId",
                        new MapSqlParameterSource("studentId", studentId).addValue("lessonId", lessonId),
                        (rs, i) -> new LessonProgressRow(
                                LessonProgressStatus.fromValue(rs.getString("status")),
                                rs.getInt("passages_done"),
                                rs.getInt("passages_total")))
                .stream()
                .findFirst();
    }

    /**
     * 여러 학생 × 여러 트랙(교사 화면 4.3·4.4). 학생 id → 트랙 id → 집계, 행이 있는 것만. studentIds는 호출자가 학원 범위로 확인한 값(AcademyStudents). 시각은
     * UTC로 읽는다
     */
    public Map<Long, Map<Long, TrackProgressSummary>> trackProgressOf(
            Collection<Long> studentIds, Collection<Long> trackIds) {
        if (studentIds.isEmpty() || trackIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Map<Long, TrackProgressSummary>> rows = new HashMap<>();
        jdbc.query(
                "select student_id, track_id, coverage, completed_at, total_play_ms, session_count, last_played_at"
                        + " from track_progress where student_id in (:studentIds) and track_id in (:trackIds)",
                new MapSqlParameterSource("studentIds", studentIds).addValue("trackIds", trackIds),
                rs -> {
                    BigDecimal coverage = rs.getBigDecimal("coverage");
                    long trackId = rs.getLong("track_id");
                    if (coverage == null || coverage.signum() < 0 || coverage.compareTo(BigDecimal.ONE) > 0) {
                        throw new IllegalStateException("청취율은 0~1이어야 한다: track " + trackId + " " + coverage);
                    }
                    rows.computeIfAbsent(rs.getLong("student_id"), id -> new HashMap<>())
                            .put(
                                    trackId,
                                    new TrackProgressSummary(
                                            coverage,
                                            rs.getObject("completed_at") != null,
                                            rs.getLong("total_play_ms"),
                                            rs.getInt("session_count"),
                                            rs.getObject("last_played_at", LocalDateTime.class)
                                                    .toInstant(ZoneOffset.UTC)));
                });
        return Collections.unmodifiableMap(rows);
    }

    /** 여러 학생 × 여러 회차(교사 화면). 학생 id → 회차 id → 진도, 진도 행이 있는 것만. completed_at은 UTC로 읽는다 */
    public Map<Long, Map<Long, LessonProgressSummary>> lessonProgressOf(
            Collection<Long> studentIds, Collection<Long> lessonIds) {
        if (studentIds.isEmpty() || lessonIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Map<Long, LessonProgressSummary>> rows = new HashMap<>();
        jdbc.query(
                "select student_id, lesson_id, status, required_done, required_total, completed_at"
                        + " from lesson_progress where student_id in (:studentIds) and lesson_id in (:lessonIds)",
                new MapSqlParameterSource("studentIds", studentIds).addValue("lessonIds", lessonIds),
                rs -> {
                    LocalDateTime completedAt = rs.getObject("completed_at", LocalDateTime.class);
                    rows.computeIfAbsent(rs.getLong("student_id"), id -> new HashMap<>())
                            .put(
                                    rs.getLong("lesson_id"),
                                    new LessonProgressSummary(
                                            LessonProgressStatus.fromValue(rs.getString("status")),
                                            rs.getInt("required_done"),
                                            rs.getInt("required_total"),
                                            completedAt == null ? null : completedAt.toInstant(ZoneOffset.UTC)));
                });
        return Collections.unmodifiableMap(rows);
    }
}
