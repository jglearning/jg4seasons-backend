package com.jglearning.jg4seasons.wordtest.persistence;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 채점 작업 큐(grading_job). 선점은 {@code SELECT … FOR UPDATE SKIP LOCKED} — 워커가 여럿(배포 전환 중 두 슬롯)이어도 한 작업은 한 워커만 잡고, 잡힌 행은 기다리지 않고
 * 건너뛴다. 모든 메서드는 부르는 쪽 트랜잭션 안에서(MANDATORY). 시각은 UTC·초 단위 내림
 */
@Component
public class GradingJobStore {

    /** 잡은 작업. attempts = 지금까지 실패한 횟수 */
    public record ClaimedJob(long jobId, long submissionId, int attempts) {}

    private final NamedParameterJdbcTemplate jdbc;

    public GradingJobStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** queued이고 run_after ≤ now인 작업 하나(가장 이른 것)를 running으로. 없거나 모두 다른 워커가 잡고 있으면 empty */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<ClaimedJob> claimNext(Instant now) {
        Optional<ClaimedJob> job = jdbc
                .query(
                        "select id, submission_id, attempts from grading_job where status = 'queued' and run_after <= :now"
                                + " order by run_after, id limit 1 for update skip locked",
                        Map.of("now", utc(now)),
                        (rs, i) -> new ClaimedJob(rs.getLong("id"), rs.getLong("submission_id"), rs.getInt("attempts")))
                .stream()
                .findFirst();
        job.ifPresent(claimed -> jdbc.update(
                "update grading_job set status = 'running', locked_at = :now where id = :id",
                Map.of("id", claimed.jobId(), "now", utc(now))));
        return job;
    }

    /** running → done. running이 아니면(이미 다른 결과로 바뀜) false, 아무것도 바꾸지 않음 */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean done(long jobId) {
        return jdbc.update(
                        "update grading_job set status = 'done' where id = :id and status = 'running'",
                        Map.of("id", jobId))
                == 1;
    }

    /** 다시 해보기: running → queued, runAfter 뒤에 잡히게. error는 실패 종류와 짧은 설명만(원문 없음). running이 아니면 false */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean retry(long jobId, int attempts, Instant runAfter, String error) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", jobId);
        params.put("attempts", attempts);
        params.put("runAfter", utc(runAfter));
        params.put("error", truncate(error));
        return jdbc.update(
                        "update grading_job set status = 'queued', attempts = :attempts, run_after = :runAfter,"
                                + " last_error = :error, locked_at = null where id = :id and status = 'running'",
                        params)
                == 1;
    }

    /** 포기: running → dead, 다시 잡지 않는다. running이 아니면 false */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean dead(long jobId, int attempts, String error) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", jobId);
        params.put("attempts", attempts);
        params.put("error", truncate(error));
        return jdbc.update(
                        "update grading_job set status = 'dead', attempts = :attempts, last_error = :error"
                                + " where id = :id and status = 'running'",
                        params)
                == 1;
    }

    /** last_error VARCHAR(500) */
    private static String truncate(String error) {
        return error == null || error.length() <= 500 ? error : error.substring(0, 500);
    }

    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant.truncatedTo(ChronoUnit.SECONDS), ZoneOffset.UTC);
    }
}
