package com.jglearning.jg4seasons.wordtest;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.MutableClock;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.infra.storage.MemoryPhotoStorage;
import com.jglearning.jg4seasons.wordtest.reading.FakeAnswerSheetReader;
import java.time.Instant;
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

/** 워커 둘이 동시에 돌아도(배포 전환 중 두 슬롯) 한 작업은 한 번만 읽고 답안은 한 벌. 실제 커밋이 필요해 @Transactional 없이, 끝에 지운다 */
@IntegrationTest
class GradingWorkerCommitTest {

    @Autowired
    GradingWorker worker;

    @Autowired
    FakeAnswerSheetReader reader;

    @Autowired
    MemoryPhotoStorage photos;

    @Autowired
    MutableClock clock;

    @Autowired
    JdbcTemplate jdbc;

    long academyId;
    long studentId;
    long lessonId;
    long submissionId;

    @BeforeEach
    void setUp() {
        clock.set(Instant.parse("2026-11-26T03:00:00Z"));
        TestData data = new TestData(jdbc);
        academyId = data.academy("동시 채점 학원");
        studentId = data.student(academyId, "동시 채점 학생");
        lessonId = data.lessonId("L2-B1-01");
        jdbc.update(
                "insert into word_test_item (lesson_id, section, no, prompt, answer) values (?, 'B', 1, '뜻', 'word')",
                lessonId);
        String key = "homework/" + academyId + "/" + studentId + "/" + UUID.randomUUID() + ".jpg";
        photos.put(key, new byte[] {1}, "image/jpeg");
        jdbc.update(
                "insert into word_test_submission (client_submission_id, student_id, lesson_id, section, attempt_no,"
                        + " image_key, status, total, created_at) values (?, ?, ?, 'B', 1, ?, 'pending', 1, '2026-11-26 03:00:00')",
                UUID.randomUUID().toString(),
                studentId,
                lessonId,
                key);
        submissionId = jdbc.queryForObject("select last_insert_id()", Long.class);
        jdbc.update(
                "insert into grading_job (submission_id, status, run_after) values (?, 'queued', '2000-01-01 00:00:00')",
                submissionId);
    }

    @AfterEach
    void cleanUp() {
        clock.reset();
        photos.clear();
        reader.reset();
        jdbc.update("delete from word_test_answer where submission_id = ?", submissionId);
        jdbc.update("delete from grading_job where submission_id = ?", submissionId);
        jdbc.update("delete from word_test_submission where id = ?", submissionId);
        jdbc.update("delete from word_test_item where lesson_id = ?", lessonId);
        jdbc.update("delete from `user` where academy_id = ?", academyId);
        jdbc.update("delete from academy where id = ?", academyId);
    }

    @Test
    void A가_읽는_동안_돈_워커_B는_그_작업을_잡지_않고_바로_끝난다() throws Exception {
        // 선점 순간의 경합(SKIP LOCKED)은 GradingJobStoreCommitTest가 본다. 여기서는 A의 외부 호출 도중 B가 실제로 겹쳐 도는 경우
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch aReading = new CountDownLatch(1);
        CountDownLatch bDone = new CountDownLatch(1);
        reader.duringRead(count -> {
            aReading.countDown();
            try {
                bDone.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        Future<Integer> a = pool.submit(() -> worker.runOnce());
        assertThat(aReading.await(10, TimeUnit.SECONDS)).as("A가 읽기에 들어감").isTrue();
        Future<Integer> b = pool.submit(() -> {
            try {
                return worker.runOnce();
            } finally {
                bDone.countDown();
            }
        });

        assertThat(b.get(5, TimeUnit.SECONDS)).as("B는 A를 기다리지 않고 0").isZero();
        assertThat(a.get(10, TimeUnit.SECONDS)).isEqualTo(1);
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        assertThat(reader.calls()).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "select count(*) from word_test_answer where submission_id = ?", Integer.class, submissionId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "select status from word_test_submission where id = ?", String.class, submissionId))
                .isEqualTo("graded");
    }
}
