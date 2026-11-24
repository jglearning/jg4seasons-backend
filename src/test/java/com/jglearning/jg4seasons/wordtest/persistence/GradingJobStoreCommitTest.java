package com.jglearning.jg4seasons.wordtest.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.wordtest.persistence.GradingJobStore.ClaimedJob;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 채점 작업 선점(SELECT … FOR UPDATE SKIP LOCKED). 실제 커밋이 필요해 @Transactional 없이 돌리고 끝에 지운다. 다른 테스트가 남긴 작업과 섞이지 않게 이 테스트의 작업은 run_after를
 * 아주 이른 시각으로 두고(먼저 잡힘) 내 작업 id로만 확인한다
 */
@IntegrationTest
class GradingJobStoreCommitTest {

    static final Instant NOW = Instant.parse("2026-11-26T03:00:00Z");
    static final LocalDateTime EARLY = LocalDateTime.of(2000, 1, 1, 0, 0);

    @Autowired
    GradingJobStore jobs;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PlatformTransactionManager transactions;

    TestData data;
    long academyId;
    long studentId;
    long submissionId;
    long jobId;

    @BeforeEach
    void setUp() {
        data = new TestData(jdbc);
        academyId = data.academy("작업 학원");
        studentId = data.student(academyId, "작업 학생");
        jdbc.update(
                "insert into word_test_submission (client_submission_id, student_id, lesson_id, section, attempt_no,"
                        + " status, total, created_at) values (?, ?, ?, 'B', 1, 'pending', 3, ?)",
                UUID.randomUUID().toString(),
                studentId,
                data.lessonId("L2-B1-01"),
                EARLY);
        submissionId = jdbc.queryForObject("select last_insert_id()", Long.class);
        jdbc.update(
                "insert into grading_job (submission_id, status, run_after) values (?, 'queued', ?)",
                submissionId,
                EARLY);
        jobId = jdbc.queryForObject("select last_insert_id()", Long.class);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from grading_job where submission_id = ?", submissionId);
        jdbc.update("delete from word_test_submission where student_id = ?", studentId);
        jdbc.update("delete from `user` where academy_id = ?", academyId);
        jdbc.update("delete from academy where id = ?", academyId);
    }

    @Test
    void 잡으면_running이고_locked_at이_찍힌다() {
        Optional<ClaimedJob> claimed = claim();

        assertThat(claimed).contains(new ClaimedJob(jobId, submissionId, 0));
        assertThat(job())
                .containsEntry("status", "running")
                .containsEntry("locked_at", LocalDateTime.of(2026, 11, 26, 3, 0));
    }

    @Test
    void 두_트랜잭션이_동시에_잡으면_한쪽만_내_작업을_잡는다() throws Exception {
        CountDownLatch firstClaimed = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<Optional<ClaimedJob>> first = pool.submit(() -> tx().execute(status -> {
            Optional<ClaimedJob> job = jobs.claimNext(NOW);
            firstClaimed.countDown();
            await(release);
            return job;
        }));
        await(firstClaimed);
        // 앞 트랜잭션이 행 잠금을 쥔 채 커밋하지 않은 동안 — SKIP LOCKED라 기다리지 않고 건너뛴다
        Future<Optional<ClaimedJob>> second = pool.submit(() -> tx().execute(status -> jobs.claimNext(NOW)));
        Optional<ClaimedJob> secondResult = second.get(10, TimeUnit.SECONDS);
        release.countDown();
        Optional<ClaimedJob> firstResult = first.get(10, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(firstResult.map(ClaimedJob::jobId)).contains(jobId);
        assertThat(secondResult.map(ClaimedJob::jobId)).isNotEqualTo(Optional.of(jobId));
    }

    @Test
    void running인_작업은_다시_잡지_않는다() {
        claim();

        assertThat(claim().map(ClaimedJob::jobId)).isNotEqualTo(Optional.of(jobId));
    }

    @Test
    void run_after가_지나지_않은_작업은_잡지_않는다() {
        jdbc.update("update grading_job set run_after = '2026-11-26 03:00:01' where id = ?", jobId);

        assertThat(claim().map(ClaimedJob::jobId)).isNotEqualTo(Optional.of(jobId));
    }

    @Test
    void 다시_해보기로_돌리면_시각이_지난_뒤_시도_횟수와_함께_다시_잡힌다() {
        claim();
        tx().executeWithoutResult(status -> jobs.retry(jobId, 1, NOW.plusSeconds(30), "TIMEOUT: 30초"));

        assertThat(job())
                .containsEntry("status", "queued")
                .containsEntry("last_error", "TIMEOUT: 30초")
                .containsEntry("locked_at", null);
        assertThat(claim().map(ClaimedJob::jobId)).isNotEqualTo(Optional.of(jobId));
        Optional<ClaimedJob> later = tx().execute(status -> jobs.claimNext(NOW.plusSeconds(30)));
        assertThat(later).contains(new ClaimedJob(jobId, submissionId, 1));
    }

    @Test
    void 끝과_포기를_기록한다() {
        claim();
        tx().executeWithoutResult(status -> jobs.dead(jobId, 3, "COUNT_MISMATCH: 문항 3개인데 2개"));

        assertThat(job()).containsEntry("status", "dead").containsEntry("last_error", "COUNT_MISMATCH: 문항 3개인데 2개");
        assertThat(((Number) job().get("attempts")).intValue()).isEqualTo(3);

        jdbc.update("update grading_job set status = 'running' where id = ?", jobId);
        tx().executeWithoutResult(status -> jobs.done(jobId));
        assertThat(job()).containsEntry("status", "done");
    }

    @Test
    void running이_아닌_작업은_끝_다시_포기를_기록하지_않고_false() {
        claim();
        Boolean first = tx().execute(status -> jobs.done(jobId));
        assertThat(first).isTrue();

        Boolean dead = tx().execute(status -> jobs.dead(jobId, 3, "API_ERROR: x"));
        Boolean retry = tx().execute(status -> jobs.retry(jobId, 1, NOW.plusSeconds(30), "TIMEOUT: x"));
        Boolean again = tx().execute(status -> jobs.done(jobId));
        assertThat(dead).isFalse();
        assertThat(retry).isFalse();
        assertThat(again).isFalse();
        assertThat(job()).containsEntry("status", "done").containsEntry("last_error", null);
    }

    private Optional<ClaimedJob> claim() {
        return tx().execute(status -> jobs.claimNext(NOW));
    }

    private TransactionTemplate tx() {
        return new TransactionTemplate(transactions);
    }

    private Map<String, Object> job() {
        return jdbc.queryForMap(
                "select status, attempts, run_after, locked_at, last_error from grading_job where id = ?", jobId);
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
