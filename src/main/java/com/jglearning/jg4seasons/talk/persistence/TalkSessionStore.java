package com.jglearning.jg4seasons.talk.persistence;

import com.jglearning.jg4seasons.talk.TalkEndReason;
import com.jglearning.jg4seasons.talk.TalkSessionStatus;
import com.jglearning.jg4seasons.talk.TalkTopic;
import com.jglearning.jg4seasons.talk.TalkTranscript.Trimmed;
import com.jglearning.jg4seasons.talk.TalkTranscript.Turn;
import com.jglearning.jg4seasons.talk.TalkVoice;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
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
            TalkTopic topic,
            TalkVoice voice,
            TalkSessionStatus status,
            int maxSeconds,
            String model,
            Instant createdAt) {}

    /** 새 세션. status는 created 또는 failed(발급 실패 기록) */
    public record NewTalkSession(
            String clientId,
            long studentId,
            Long lessonId,
            String levelCode,
            TalkTopic topic,
            TalkVoice voice,
            TalkSessionStatus status,
            int maxSeconds,
            String model,
            String promptVersion) {

        public NewTalkSession {
            // 새 행은 발급 성공(created)·실패 기록(failed)뿐 — 종료 기록 없는 ended 행이 생기지 않게(리뷰 8)
            if (status != TalkSessionStatus.CREATED && status != TalkSessionStatus.FAILED) {
                throw new IllegalArgumentException("새 세션은 created·failed만: " + status);
            }
        }

        /** 같은 세션을 다른 상태로(발급 실패 기록) — 칸을 손으로 복사하지 않게(#76 리뷰 2) */
        public NewTalkSession withStatus(TalkSessionStatus newStatus) {
            return new NewTalkSession(
                    clientId,
                    studentId,
                    lessonId,
                    levelCode,
                    topic,
                    voice,
                    newStatus,
                    maxSeconds,
                    model,
                    promptVersion);
        }
    }

    /**
     * 종료·조회 응답(계약 D-3·D-4)의 재료. 종료 전이면 durationSec·studentTurnCount null, 요약이 없으면(전·실패·요약 중) summaryJson null
     */
    public record TalkSessionView(
            long id, TalkSessionStatus status, Integer durationSec, Integer studentTurnCount, String summaryJson) {}

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
                        .addValue("topic", s.topic().code())
                        .addValue("voice", s.voice().code())
                        .addValue("status", s.status().code())
                        .addValue("max", s.maxSeconds())
                        .addValue("model", s.model())
                        .addValue("version", s.promptVersion())
                        .addValue("at", at),
                keys,
                new String[] {"id"});
        return Objects.requireNonNull(keys.getKey()).longValue();
    }

    /**
     * 본인 세션 잠금 읽기(종료 — 같은 세션 종료 2건이 한 번만 저장되게). 부르는 쪽 트랜잭션 안에서. 남의 것·없는 것 모두 empty(학원 칸이 없어 학생 id 비교가 학원 경계를
     * 겸한다)
     */
    public Optional<TalkSessionRow> lockOwned(long id, long studentId) {
        return jdbc
                .query(
                        "select " + COLUMNS + " from talk_session where id = :id and student_id = :student for update",
                        Map.of("id", id, "student", studentId),
                        TalkSessionStore::row)
                .stream()
                .findFirst();
    }

    /** 종료 기록. usageJson은 JSON 객체 문자열 또는 null */
    public void markEnded(long id, Trimmed trimmed, TalkEndReason reason, String usageJson, Instant now) {
        LocalDateTime at = utc(now);
        jdbc.update(
                "update talk_session set status = :status, duration_sec = :duration, student_turn_count = :students,"
                        + " end_reason = :reason, usage_json = :usage, ended_at = :at, updated_at = :at where id = :id",
                new MapSqlParameterSource()
                        .addValue("status", TalkSessionStatus.ENDED.code())
                        .addValue("duration", trimmed.durationSec())
                        .addValue("students", trimmed.studentTurnCount())
                        .addValue("reason", reason.code())
                        .addValue("usage", usageJson)
                        .addValue("at", at)
                        .addValue("id", id));
    }

    /** 글자 기록(이미 자른 줄). 음성은 저장하지 않는다 */
    public void insertTurns(long sessionId, List<Turn> turns) {
        if (turns.isEmpty()) {
            return;
        }
        MapSqlParameterSource[] rows = turns.stream()
                .map(turn -> new MapSqlParameterSource()
                        .addValue("session", sessionId)
                        .addValue("seq", turn.seq())
                        .addValue("role", turn.role().code())
                        .addValue("text", turn.text()))
                .toArray(MapSqlParameterSource[]::new);
        jdbc.batchUpdate(
                "insert into talk_turn (session_id, seq, role, text) values (:session, :seq, :role, :text)", rows);
    }

    /** 요약(JSON 문자열) */
    public void saveSummary(long id, String summaryJson) {
        jdbc.update(
                "update talk_session set summary_json = :summary where id = :id",
                Map.of("summary", summaryJson, "id", id));
    }

    /** 본인 세션 조회(D-4). 남의 것·없는 것 모두 empty */
    public Optional<TalkSessionView> findOwned(long id, long studentId) {
        return jdbc
                .query(
                        "select id, status, duration_sec, student_turn_count, summary_json from talk_session"
                                + " where id = :id and student_id = :student",
                        Map.of("id", id, "student", studentId),
                        (rs, rowNum) -> new TalkSessionView(
                                rs.getLong("id"),
                                enumOf(TalkSessionStatus.ofCode(rs.getString("status")), "status", rs),
                                rs.getObject("duration_sec", Integer.class),
                                rs.getObject("student_turn_count", Integer.class),
                                rs.getString("summary_json")))
                .stream()
                .findFirst();
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

    /** 하루 한도용: from 이후 만든 세션 중 발급에 실패(failed)하지 않았고, 연결 전에 떠난 0초 세션도 아닌 것 */
    /**
     * @param freeWindowSeconds 연결 전 이탈로 볼 시간(만든 뒤 이 안에 종료). 임시 키 수명 — 이보다 늦은 종료는 연결해 대화했을 수 있어 0초로 보내도 셈(서버 시계,
     *     보안 리뷰 #86)
     */
    public int countIssuedSince(long studentId, Instant from, int freeWindowSeconds) {
        Integer count = jdbc.queryForObject(
                "select count(*) from talk_session where student_id = :student and created_at >= :from"
                        + " and status <> :failed"
                        // 연결 전에 떠난 세션(종료·대화 시간 0·학생 줄 0)은 대화가 없었음 — 세지 않음(#86, 사용자 11/26).
                        // 시간·줄은 브라우저가 보낸 값이라 믿을 수 없음(ADR 0039 받아들인 위험, #78)
                        + " and not (status = :ended and duration_sec = 0 and student_turn_count = 0"
                        + " and timestampdiff(second, created_at, ended_at) <= :window)",
                Map.of(
                        "student", studentId,
                        "from", utc(from),
                        "failed", TalkSessionStatus.FAILED.code(),
                        "ended", TalkSessionStatus.ENDED.code(),
                        "window", freeWindowSeconds),
                Integer.class);
        return count == null ? 0 : count;
    }

    public static boolean isClientIdConflict(RuntimeException e) {
        String message = e.getMessage();
        return message != null && message.contains("uk_talk_session_client");
    }

    private static TalkSessionRow row(ResultSet rs, int rowNum) throws SQLException {
        return new TalkSessionRow(
                rs.getLong("id"),
                rs.getString("client_session_id"),
                rs.getLong("student_id"),
                rs.getObject("lesson_id", Long.class), // NULL = 개강 전(getLong + wasNull은 읽는 순서에 따라 0이 된다 — 리뷰 3)
                rs.getString("level_code"),
                enumOf(TalkTopic.ofCode(rs.getString("topic")), "topic", rs),
                enumOf(TalkVoice.ofCode(rs.getString("voice")), "voice", rs),
                enumOf(TalkSessionStatus.ofCode(rs.getString("status")), "status", rs),
                rs.getInt("max_seconds"),
                rs.getString("model"),
                rs.getObject("created_at", LocalDateTime.class).toInstant(ZoneOffset.UTC));
    }

    /** DB CHECK와 enum이 어긋난 것 — 데이터 오류 */
    private static <T> T enumOf(Optional<T> value, String column, ResultSet rs) throws SQLException {
        long id = rs.getLong("id");
        return value.orElseThrow(() -> new IllegalStateException("talk_session." + column + " 값이 잘못됐다: " + id));
    }

    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
