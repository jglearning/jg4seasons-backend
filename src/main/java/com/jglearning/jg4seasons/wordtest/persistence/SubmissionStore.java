package com.jglearning.jg4seasons.wordtest.persistence;

import com.jglearning.jg4seasons.wordtest.grading.Section;
import com.jglearning.jg4seasons.wordtest.grading.SubmissionStatus;
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
 * 단어시험 제출·채점 작업 표(학원 칸 없음 — 부르는 쪽이 JWT 학생으로 거른다). 시각은 UTC LocalDateTime으로 넣고 Instant로 꺼낸다. 트랜잭션은 부르는 쪽이 연다
 */
@Component
public class SubmissionStore {

    /** 제출 한 행 */
    public record SubmissionRow(
            long id,
            String clientId,
            long studentId,
            long lessonId,
            Section section,
            int attemptNo,
            SubmissionStatus status,
            Integer score,
            int total,
            Instant submittedAt,
            Instant gradedAt) {}

    /** 새 제출(status pending) */
    public record NewSubmission(
            String clientId,
            long studentId,
            long lessonId,
            Section section,
            int attemptNo,
            String imageKey,
            int total) {}

    private static final String COLUMNS =
            "id, client_submission_id, student_id, lesson_id, section, attempt_no, status,"
                    + " score, total, created_at, graded_at";

    private final NamedParameterJdbcTemplate jdbc;

    public SubmissionStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 일반 읽기(REPEATABLE READ 스냅숏) */
    public Optional<SubmissionRow> findByClientId(String clientId) {
        return one("select " + COLUMNS + " from word_test_submission where client_submission_id = :client", clientId);
    }

    /** 잠금 읽기 — 스냅숏이 아닌 커밋된 최신 행(동시 제출이 UNIQUE에 걸린 뒤 다시 읽을 때) */
    public Optional<SubmissionRow> lockByClientId(String clientId) {
        return one(
                "select " + COLUMNS + " from word_test_submission where client_submission_id = :client for update",
                clientId);
    }

    /** 그 학생·회차·영역의 제출 중 created_at ≥ fromUtc인 것의 수(하루 횟수) */
    public int countSince(long studentId, long lessonId, Section section, Instant from) {
        Integer count = jdbc.queryForObject(
                "select count(*) from word_test_submission where student_id = :student and lesson_id = :lesson"
                        + " and section = :section and created_at >= :from",
                Map.of("student", studentId, "lesson", lessonId, "section", section.dbValue(), "from", utc(from)),
                Integer.class);
        return count == null ? 0 : count;
    }

    /** 그 학생·회차·영역의 마지막 시도 번호, 없으면 0 */
    public int maxAttempt(long studentId, long lessonId, Section section) {
        Integer max = jdbc.queryForObject(
                "select coalesce(max(attempt_no), 0) from word_test_submission where student_id = :student"
                        + " and lesson_id = :lesson and section = :section",
                Map.of("student", studentId, "lesson", lessonId, "section", section.dbValue()),
                Integer.class);
        return max == null ? 0 : max;
    }

    /**
     * 제출(pending)과 채점 작업(queued, run_after = 제출 시각)을 넣는다. 같은 트랜잭션 안에서만. UNIQUE 위반(같은 client id·같은 시도 번호)은
     * DuplicateKeyException 그대로 — 제약 이름은 {@link #isClientIdConflict}
     */
    public long insertWithJob(NewSubmission s, Instant now) {
        LocalDateTime at = utc(now);
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(
                "insert into word_test_submission (client_submission_id, student_id, lesson_id, section, attempt_no,"
                        + " image_key, status, total, created_at, updated_at)"
                        + " values (:client, :student, :lesson, :section, :attempt, :key, :status, :total, :at, :at)",
                new MapSqlParameterSource()
                        .addValue("client", s.clientId())
                        .addValue("student", s.studentId())
                        .addValue("lesson", s.lessonId())
                        .addValue("section", s.section().dbValue())
                        .addValue("attempt", s.attemptNo())
                        .addValue("key", s.imageKey())
                        .addValue("status", SubmissionStatus.PENDING.dbValue())
                        .addValue("total", s.total())
                        .addValue("at", at),
                keys,
                new String[] {"id"});
        long id = Objects.requireNonNull(keys.getKey(), "생성된 키 없음").longValue();
        jdbc.update(
                "insert into grading_job (submission_id, status, run_after, created_at, updated_at)"
                        + " values (:submission, 'queued', :at, :at, :at)",
                Map.of("submission", id, "at", at));
        return id;
    }

    /** UNIQUE 위반 메시지가 client id 제약이면 true(같은 요청이 동시에 들어온 경우) */
    public static boolean isClientIdConflict(RuntimeException e) {
        String message = e.getMessage();
        return message != null && message.contains("uk_word_test_submission_client");
    }

    private Optional<SubmissionRow> one(String sql, String clientId) {
        return jdbc.query(sql, Map.of("client", clientId), SubmissionStore::row).stream()
                .findFirst();
    }

    static SubmissionRow row(ResultSet rs, int rowNum) throws SQLException {
        LocalDateTime graded = rs.getObject("graded_at", LocalDateTime.class);
        return new SubmissionRow(
                rs.getLong("id"),
                rs.getString("client_submission_id"),
                rs.getLong("student_id"),
                rs.getLong("lesson_id"),
                Section.fromDb(rs.getString("section")),
                rs.getInt("attempt_no"),
                SubmissionStatus.fromDb(rs.getString("status")),
                rs.getObject("score", Integer.class),
                rs.getInt("total"),
                rs.getObject("created_at", LocalDateTime.class).toInstant(ZoneOffset.UTC),
                graded == null ? null : graded.toInstant(ZoneOffset.UTC));
    }

    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
