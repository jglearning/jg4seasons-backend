package com.jglearning.jg4seasons.wordtest.persistence;

import com.jglearning.jg4seasons.wordtest.grading.Section;
import com.jglearning.jg4seasons.wordtest.grading.SubmissionStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
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
            int total) {
        public NewSubmission {
            if (attemptNo < 1) {
                throw new IllegalArgumentException("시도 번호는 1부터");
            }
        }
    }

    /**
     * 채점할 제출.
     *
     * @param imageKey 사진 키, 사진을 지웠으면 null(그때는 채점하지 않고 failed)
     */
    public record GradingTarget(
            long id, long lessonId, Section section, String imageKey, SubmissionStatus status, int total) {}

    private static final String COLUMNS =
            "id, client_submission_id, student_id, lesson_id, section, attempt_no, status,"
                    + " score, total, created_at, graded_at";

    private final NamedParameterJdbcTemplate jdbc;

    public SubmissionStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<GradingTarget> findForGrading(long id) {
        return jdbc
                .query(
                        "select id, lesson_id, section, image_key, status, total from word_test_submission where id = :id",
                        Map.of("id", id),
                        (rs, i) -> new GradingTarget(
                                rs.getLong("id"),
                                rs.getLong("lesson_id"),
                                Section.fromDb(rs.getString("section")),
                                rs.getString("image_key"),
                                SubmissionStatus.fromDb(rs.getString("status")),
                                rs.getInt("total")))
                .stream()
                .findFirst();
    }

    /**
     * pending → grading. 이미 grading이면(재시도) 그대로 true. 그 밖의 상태(graded·failed 등)면 false — 처리하지 않는다.
     * 상태 변경은 이 셋(markGrading·markGraded·markFailed)에서만, 모두 조건부 UPDATE
     */
    public boolean markGrading(long id) {
        SubmissionStatus.PENDING.moveTo(SubmissionStatus.GRADING);
        if (moveFrom(id, SubmissionStatus.PENDING, SubmissionStatus.GRADING, Map.of())) {
            return true;
        }
        return findForGrading(id)
                .map(target -> target.status() == SubmissionStatus.GRADING)
                .orElse(false);
    }

    /** grading → graded, 점수·채점 시각·모델·프롬프트 버전을 함께. grading이 아니면(이미 채점됨 등) false, 아무것도 바꾸지 않음 */
    public boolean markGraded(long id, int score, Instant gradedAt, String model, String promptVersion) {
        SubmissionStatus.GRADING.moveTo(SubmissionStatus.GRADED);
        Map<String, Object> extra = new HashMap<>();
        extra.put("score", score);
        extra.put("gradedAt", utc(gradedAt.truncatedTo(ChronoUnit.SECONDS)));
        extra.put("model", model);
        extra.put("promptVersion", promptVersion);
        return moveFrom(
                id,
                SubmissionStatus.GRADING,
                SubmissionStatus.GRADED,
                extra,
                ", score = :score, graded_at = :gradedAt, model = :model, prompt_version = :promptVersion");
    }

    /** pending·grading → failed(점수·채점 시각은 비운 채). 그 밖이면 false */
    public boolean markFailed(long id) {
        SubmissionStatus.GRADING.moveTo(SubmissionStatus.FAILED);
        if (moveFrom(id, SubmissionStatus.GRADING, SubmissionStatus.FAILED, Map.of())) {
            return true;
        }
        // 선점 직후(아직 pending) 사진이 없어 바로 실패하는 경우 — pending → failed는 1단계 전이 표에 없어 grading을 거친 것으로 본다
        return markGrading(id) && moveFrom(id, SubmissionStatus.GRADING, SubmissionStatus.FAILED, Map.of());
    }

    private boolean moveFrom(long id, SubmissionStatus from, SubmissionStatus to, Map<String, Object> extra) {
        return moveFrom(id, from, to, extra, "");
    }

    private boolean moveFrom(
            long id, SubmissionStatus from, SubmissionStatus to, Map<String, Object> extra, String extraColumns) {
        Map<String, Object> params = new HashMap<>(extra);
        params.put("id", id);
        params.put("from", from.dbValue());
        params.put("to", to.dbValue());
        return jdbc.update(
                        "update word_test_submission set status = :to" + extraColumns
                                + " where id = :id and status = :from",
                        params)
                == 1;
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

    /** 그 학생의 제출이면(학원 칸이 없어 학생 id 비교가 학원 경계를 겸한다). 남의 것·없는 것 모두 empty */
    public Optional<SubmissionRow> findOwned(long id, long studentId) {
        return jdbc
                .query(
                        "select " + COLUMNS + " from word_test_submission where id = :id and student_id = :student",
                        Map.of("id", id, "student", studentId),
                        SubmissionStore::row)
                .stream()
                .findFirst();
    }

    /** 그 학생·회차·영역의 마지막 시도 */
    public Optional<SubmissionRow> latest(long studentId, long lessonId, Section section) {
        return jdbc
                .query(
                        "select " + COLUMNS + " from word_test_submission where student_id = :student"
                                + " and lesson_id = :lesson and section = :section order by attempt_no desc limit 1",
                        Map.of("student", studentId, "lesson", lessonId, "section", section.dbValue()),
                        SubmissionStore::row)
                .stream()
                .findFirst();
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
