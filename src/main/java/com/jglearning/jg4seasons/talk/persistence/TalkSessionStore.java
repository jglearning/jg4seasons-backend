package com.jglearning.jg4seasons.talk.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

/**
 * 프리토킹 세션 표(학원 칸 없음 — 부르는 쪽이 JWT 학생으로 거른다). 시각은 UTC LocalDateTime으로 넣는다. 트랜잭션은 열지 않는다(외부 발급 호출을 사이에 두므로 문장마다
 * 커밋)
 */
@Component
public class TalkSessionStore {

    /** 세션 한 행(1단계 생성에 쓰는 칸만) */
    public record TalkSessionRow(
            long id,
            String clientId,
            long studentId,
            Long lessonId,
            String levelCode,
            String topic,
            String voice,
            String status,
            int maxSeconds,
            String model,
            Instant createdAt) {}

    /** 새 세션. status는 created 또는 failed(발급 실패 기록) */
    public record NewTalkSession(
            String clientId,
            long studentId,
            Long lessonId,
            String levelCode,
            String topic,
            String voice,
            String status,
            int maxSeconds,
            String model,
            String promptVersion) {}

    private static final String COLUMNS =
            "id, client_session_id, student_id, lesson_id, level_code, topic, voice, status, max_seconds, model,"
                    + " created_at";

    private final NamedParameterJdbcTemplate jdbc;

    TalkSessionStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** UNIQUE(client_session_id)에 걸리면 DuplicateKeyException 그대로 — {@link #isClientIdConflict} */
    public long insert(NewTalkSession s, Instant now) {
        LocalDateTime at = utc(now);
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(
                "insert into talk_session (client_session_id, student_id, lesson_id, level_code, topic, voice, status,"
                        + " max_seconds, model, prompt_version, created_at, updated_at) values (:client, :student,"
                        + " :lesson, :level, :topic, :voice, :status, :max, :model, :version, :at, :at)",
                new MapSqlParameterSource()
                        .addValue("client", s.clientId())
                        .addValue("student", s.studentId())
                        .addValue("lesson", s.lessonId())
                        .addValue("level", s.levelCode())
                        .addValue("topic", s.topic())
                        .addValue("voice", s.voice())
                        .addValue("status", s.status())
                        .addValue("max", s.maxSeconds())
                        .addValue("model", s.model())
                        .addValue("version", s.promptVersion())
                        .addValue("at", at),
                keys,
                new String[] {"id"});
        return Objects.requireNonNull(keys.getKey()).longValue();
    }

    public Optional<TalkSessionRow> findByClientId(String clientId) {
        return jdbc
                .query(
                        "select " + COLUMNS + " from talk_session where client_session_id = :client",
                        Map.of("client", clientId),
                        TalkSessionStore::row)
                .stream()
                .findFirst();
    }

    /** 하루 한도용: from 이후 만든 세션 중 발급에 실패(failed)하지 않은 것 */
    public int countIssuedSince(long studentId, Instant from) {
        Integer count = jdbc.queryForObject(
                "select count(*) from talk_session where student_id = :student and created_at >= :from"
                        + " and status <> 'failed'",
                Map.of("student", studentId, "from", utc(from)),
                Integer.class);
        return count == null ? 0 : count;
    }

    public static boolean isClientIdConflict(RuntimeException e) {
        String message = e.getMessage();
        return message != null && message.contains("uk_talk_session_client");
    }

    private static TalkSessionRow row(ResultSet rs, int rowNum) throws SQLException {
        long lesson = rs.getLong("lesson_id");
        return new TalkSessionRow(
                rs.getLong("id"),
                rs.getString("client_session_id"),
                rs.getLong("student_id"),
                rs.wasNull() ? null : lesson,
                rs.getString("level_code"),
                rs.getString("topic"),
                rs.getString("voice"),
                rs.getString("status"),
                rs.getInt("max_seconds"),
                rs.getString("model"),
                rs.getObject("created_at", LocalDateTime.class).toInstant(ZoneOffset.UTC));
    }

    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
