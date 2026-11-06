package com.jglearning.jg4seasons.listening.persistence;

import com.jglearning.jg4seasons.listening.api.LessonProgressStatus;
import com.jglearning.jg4seasons.listening.persistence.ProgressStore.LessonProgressRow;
import com.jglearning.jg4seasons.listening.segment.Range;
import com.jglearning.jg4seasons.listening.segment.SegmentFilter.Accepted;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * flush 쓰기(03 §3.1): 청취 세션 upsert, 세그먼트 추가(원본 — INSERT만, ADR-06), 집계 행 확보·잠금·갱신. 이 테이블들에는 academy_id가 없다 — 호출자가
 * 학생의 학원 반 소속·회차 열림을 먼저 확인한다. 시각은 UTC LocalDateTime으로 쓰고 읽는다(JVM 시간대와 무관하게).
 *
 * <p>잠금 순서는 호출자(FlushService)가 지킨다: 세션 행(upsertSession — 같은 세션 요청을 줄 세워, 뒤 요청이 앞 요청의 구간을 receivedRanges로
 * 보고 같은 구간을 다시 저장하지 않게 한다) → 회차 행(lockLessonProgress — 같은 학생·같은 회차를 줄 세운다) → 트랙 행(lockTrackProgress) → 다른 트랙
 * 잠금 읽기(completedTracksForShare). 회차 행을 잡은 뒤 다른 세션 행을 잠그면 순환이 생길 수 있다.
 * 행 확보는 {@code INSERT … ON DUPLICATE KEY UPDATE <키> = <키>}(아무것도 바꾸지 않음) — 동시 첫 요청은 PK·UNIQUE로 한쪽만 만들고, 이미 있는
 * 행에는 처음부터 배타 잠금을 잡는다. INSERT IGNORE는 이미 있는 행에 공유 잠금을 잡아, 두 요청이 이어지는 FOR UPDATE에서 서로 공유 잠금을 기다리는 교착이
 * 났다(커밋 테스트로 재현).
 */
@Component
public class FlushStore {

    static final int USER_AGENT_MAX = 200;

    /** studentId·trackId는 저장된 값 — 요청과 다르면 호출자가 400 */
    public record SessionRow(long id, long studentId, long trackId) {}

    /** 잠근 트랙 집계 중 갱신에 쓰는 값. completedAt은 null 가능. 세션 수는 잠근 뒤 sessionCount로 다시 센다(옛 값 + 1로 계산하지 않게 넣지 않음) */
    public record TrackProgressLock(List<Range> merged, Instant completedAt, int totalPlayMs) {
        public TrackProgressLock {
            merged = List.copyOf(merged);
        }
    }

    public record TrackProgressUpdate(
            List<Range> merged,
            int coveredMs,
            BigDecimal coverage,
            Instant completedAt,
            int totalPlayMs,
            int sessionCount,
            Instant lastPlayedAt) {
        public TrackProgressUpdate {
            merged = List.copyOf(merged);
        }
    }

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;
    private final JsonMapper jsonMapper;

    public FlushStore(JdbcTemplate jdbc, NamedParameterJdbcTemplate named, JsonMapper jsonMapper) {
        this.jdbc = jdbc;
        this.named = named;
        this.jsonMapper = jsonMapper;
    }

    /** 없으면 만든다(started_at·user_agent는 이때만). 있고 학생·트랙이 같으면 last_seen_at만 고친다. 다르면 고치지 않고 저장된 값을 돌려준다 */
    public SessionRow upsertSession(
            String clientSessionId, long studentId, long trackId, Instant startedAt, String userAgent, Instant now) {
        jdbc.update(
                "insert into listening_session"
                        + " (client_session_id, student_id, track_id, started_at, last_seen_at, user_agent)"
                        + " values (?, ?, ?, ?, ?, ?) on duplicate key update id = id",
                clientSessionId,
                studentId,
                trackId,
                utc(startedAt),
                utc(now),
                truncate(userAgent));
        SessionRow stored = jdbc.queryForObject(
                "select id, student_id, track_id from listening_session where client_session_id = ?",
                (rs, i) -> new SessionRow(rs.getLong("id"), rs.getLong("student_id"), rs.getLong("track_id")),
                clientSessionId);
        jdbc.update(
                "update listening_session set last_seen_at = ? where id = ? and student_id = ? and track_id = ?",
                utc(now),
                stored.id(),
                studentId,
                trackId);
        return stored;
    }

    public Set<Range> receivedRanges(long sessionId) {
        return new HashSet<>(jdbc.query(
                "select start_ms, end_ms from listening_segment where listening_session_id = ?",
                (rs, i) -> new Range(rs.getInt("start_ms"), rs.getInt("end_ms")),
                sessionId));
    }

    /** 그 학생·트랙의 청취 세션 수(정직성 지표 session_count). 트랙 행을 잠근 뒤 센다 */
    public int sessionCount(long studentId, long trackId) {
        Integer count = jdbc.queryForObject(
                "select count(*) from listening_session where student_id = ? and track_id = ?",
                Integer.class,
                studentId,
                trackId);
        return count == null ? 0 : count;
    }

    /** 원본 추가만 */
    public void insertSegments(long sessionId, List<Accepted> segments) {
        jdbc.batchUpdate(
                "insert into listening_segment (listening_session_id, start_ms, end_ms, recorded_at) values (?, ?, ?, ?)",
                segments,
                segments.size(),
                (ps, segment) -> {
                    ps.setLong(1, sessionId);
                    ps.setInt(2, segment.range().startMs());
                    ps.setInt(3, segment.range().endMs());
                    ps.setObject(4, utc(segment.recordedAt()));
                });
    }

    /** 회차 집계 행을 확보(없으면 not_started)하고 FOR UPDATE. 같은 학생·같은 회차 flush를 줄 세운다 — 세션 행 다음, 집계 잠금 중 첫 번째 */
    public LessonProgressRow lockLessonProgress(long studentId, long lessonId, int passagesTotal) {
        jdbc.update(
                "insert into lesson_progress (student_id, lesson_id, status, required_total, passages_total)"
                        + " values (?, ?, 'not_started', ?, ?) on duplicate key update student_id = student_id",
                studentId,
                lessonId,
                passagesTotal,
                passagesTotal);
        return jdbc.queryForObject(
                "select status, passages_done, passages_total from lesson_progress"
                        + " where student_id = ? and lesson_id = ? for update",
                (rs, i) -> new LessonProgressRow(
                        LessonProgressStatus.fromValue(rs.getString("status")),
                        rs.getInt("passages_done"),
                        rs.getInt("passages_total")),
                studentId,
                lessonId);
    }

    /** 트랙 집계 행을 확보(없으면 빈 구간)하고 FOR UPDATE */
    public TrackProgressLock lockTrackProgress(long studentId, long trackId, Instant now) {
        jdbc.update(
                "insert into track_progress (student_id, track_id, merged_ranges, last_played_at)"
                        + " values (?, ?, '[]', ?) on duplicate key update student_id = student_id",
                studentId,
                trackId,
                utc(now));
        return jdbc.queryForObject(
                "select merged_ranges, completed_at, total_play_ms"
                        + " from track_progress where student_id = ? and track_id = ? for update",
                (rs, i) -> new TrackProgressLock(
                        RangesJson.read(jsonMapper, rs.getString("merged_ranges")),
                        instant(rs.getObject("completed_at", LocalDateTime.class)),
                        rs.getInt("total_play_ms")),
                studentId,
                trackId);
    }

    /** completed_at은 처음 값을 유지한다(결정 E — null로 덮지 않음) */
    public void updateTrackProgress(long studentId, long trackId, TrackProgressUpdate update) {
        jdbc.update(
                "update track_progress set merged_ranges = ?, covered_ms = ?, coverage = ?,"
                        + " completed_at = coalesce(completed_at, ?),"
                        + " total_play_ms = ?, session_count = ?, last_played_at = ?"
                        + " where student_id = ? and track_id = ?",
                RangesJson.write(update.merged()),
                update.coveredMs(),
                update.coverage(),
                utc(update.completedAt()),
                update.totalPlayMs(),
                update.sessionCount(),
                utc(update.lastPlayedAt()),
                studentId,
                trackId);
    }

    /** 잠금 읽기(FOR SHARE) — 일반 SELECT는 트랜잭션 첫 읽기 시점 스냅샷이라 다른 flush가 막 끝낸 완료를 놓친다 */
    public Set<Long> completedTracksForShare(long studentId, Collection<Long> trackIds) {
        if (trackIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(named.queryForList(
                "select track_id from track_progress where student_id = :studentId and track_id in (:trackIds)"
                        + " and completed_at is not null for share",
                new MapSqlParameterSource("studentId", studentId).addValue("trackIds", trackIds),
                Long.class));
    }

    /** required_* = passages_*(완료 규칙이 지문마다 하나라 같다). completed_at은 처음 값을 유지한다(null로 덮지 않음) */
    public void updateLessonProgress(
            long studentId, long lessonId, LessonProgressStatus status, int done, int total, Instant completedAt) {
        jdbc.update(
                "update lesson_progress set status = ?, required_done = ?, required_total = ?, passages_done = ?,"
                        + " passages_total = ?, completed_at = coalesce(completed_at, ?)"
                        + " where student_id = ? and lesson_id = ?",
                status.value(),
                done,
                total,
                done,
                total,
                utc(completedAt),
                studentId,
                lessonId);
    }

    private static LocalDateTime utc(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant instant(LocalDateTime utc) {
        return utc == null ? null : utc.toInstant(ZoneOffset.UTC);
    }

    private static String truncate(String userAgent) {
        return userAgent == null || userAgent.length() <= USER_AGENT_MAX
                ? userAgent
                : userAgent.substring(0, USER_AGENT_MAX);
    }
}
