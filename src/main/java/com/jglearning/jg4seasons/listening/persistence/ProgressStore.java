package com.jglearning.jg4seasons.listening.persistence;

import com.jglearning.jg4seasons.listening.LessonProgressStatus;
import com.jglearning.jg4seasons.listening.segment.Range;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
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
}
