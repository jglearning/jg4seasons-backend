package com.jglearning.jg4seasons.wordtest;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.MutableClock;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.auth.AuthCookieSupport;
import com.jglearning.jg4seasons.infra.storage.MemoryPhotoStorage;
import com.jglearning.jg4seasons.wordtest.reading.FakeAnswerSheetReader;
import com.jglearning.jg4seasons.wordtest.reading.ReadFailure;
import com.jglearning.jg4seasons.wordtest.reading.ReadItem;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 채점 워커: 선점 → (잠금 없이) 사진 읽기·옮겨 적기 → 판정·저장. 워커가 자기 트랜잭션을 열므로 @Transactional 없이 돌리고 끝에 지운다. 읽기는 가짜. 1회차 B 4문항(지어낸 것)
 */
@IntegrationTest
class GradingWorkerTest {

    static final String CODE = "L2-B1-01";
    static final Instant NOON = Instant.parse("2026-11-26T03:00:00Z");

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

    @Autowired
    MockMvcTester mvc;

    @Autowired
    AuthCookieSupport cookies;

    @Autowired
    PlatformTransactionManager transactions;

    TestData data;
    long academyId;
    long classId;
    long studentId;
    long lessonId;
    long submissionId;
    long jobId;
    String key;

    @BeforeEach
    void setUp() {
        clock.set(NOON);
        data = new TestData(jdbc);
        academyId = data.academy("채점 학원");
        jdbc.update(
                "update academy set settings = '{\"timezone\":\"Asia/Seoul\",\"features\":{\"wordTest\":true}}' where id = ?",
                academyId);
        classId = data.clazz(academyId, "L2-B1", "채점 반", TestData.uniqueAccessCode(), "active");
        studentId = data.student(academyId, "채점 학생");
        data.enroll(classId, studentId);
        data.session(classId, 1, CODE, LocalDate.of(2026, 11, 26), "scheduled");
        lessonId = data.lessonId(CODE);
        String[][] rows = {{"아름다운", "beautiful"}, {"도서관", "library"}, {"받다", "receive"}, {"분위기", "atmosphere"}};
        for (int i = 0; i < rows.length; i++) {
            jdbc.update(
                    "insert into word_test_item (lesson_id, section, no, prompt, answer) values (?, 'B', ?, ?, ?)",
                    lessonId,
                    i + 1,
                    rows[i][0],
                    rows[i][1]);
        }
        key = "homework/" + academyId + "/" + studentId + "/" + UUID.randomUUID() + ".jpg";
        photos.put(key, new byte[] {1, 2, 3}, "image/jpeg");
        submissionId = submission(key);
    }

    @AfterEach
    void cleanUp() {
        clock.reset();
        photos.clear();
        reader.reset();
        jdbc.update(
                "delete from word_test_answer where submission_id in (select id from word_test_submission where student_id = ?)",
                studentId);
        jdbc.update(
                "delete from grading_job where submission_id in (select id from word_test_submission where student_id = ?)",
                studentId);
        jdbc.update("delete from word_test_submission where student_id = ?", studentId);
        jdbc.update("delete from word_test_item where lesson_id = ?", lessonId);
        jdbc.update("delete from class_session where class_id = ?", classId);
        jdbc.update("delete from enrollment where class_id = ?", classId);
        jdbc.update("delete from `class` where id = ?", classId);
        jdbc.update("delete from `user` where academy_id = ?", academyId);
        jdbc.update("delete from academy where id = ?", academyId);
    }

    @Test
    void 제출하면_워커_1회로_graded이고_점수와_문항_수만큼_답안() {
        reader.willRead(count -> List.of(
                new ReadItem(1, "beautful", false),
                new ReadItem(2, "library", false),
                new ReadItem(3, "", false),
                new ReadItem(4, "atm", true)));

        assertThat(worker.runOnce()).isEqualTo(1);

        assertThat(submissionRow())
                .containsEntry("status", "graded")
                .containsEntry("score", 1)
                .containsEntry("model", FakeAnswerSheetReader.MODEL)
                .containsEntry("prompt_version", "v1")
                .containsEntry("graded_at", LocalDateTime.of(2026, 11, 26, 3, 0));
        assertThat(jobStatus()).isEqualTo("done");
        assertThat(jdbc.queryForObject(
                        "select count(*) from word_test_answer where submission_id = ?", Integer.class, submissionId))
                .isEqualTo(4);
        assertThat(jdbc.queryForList(
                        "select a.result from word_test_answer a join word_test_item i on i.id = a.item_id"
                                + " where a.submission_id = ? order by i.no",
                        String.class,
                        submissionId))
                .containsExactly("wrong", "correct", "blank", "unclear");
        var json = assertThat(mvc.get()
                        .uri("/api/v1/word-tests/submissions/" + submissionId)
                        .cookie(cookies.studentAccess(studentId, academyId))
                        .exchange())
                .bodyJson();
        json.extractingPath("$.items[*].result").isEqualTo(List.of("wrong", "correct", "blank", "unclear"));
        json.extractingPath("$.items[*].written").isEqualTo(List.of("beautful", "library", "", ""));
        assertThat(reader.calls()).isEqualTo(1);
    }

    @Test
    void 대기_작업이_없으면_0() {
        worker.runOnce();

        assertThat(worker.runOnce()).isZero();
    }

    @Test
    void 두_번_실패하면_다시_하고_세_번째에_graded() {
        reader.willFail(ReadFailure.Kind.TIMEOUT, 2);

        assertThat(worker.runOnce()).isEqualTo(1);
        assertThat(jobRow()).containsEntry("status", "queued").containsEntry("last_error", "TIMEOUT: 가짜 실패");
        assertThat(submissionStatus()).as("재시도 동안 제출은 grading 그대로").isEqualTo("grading");
        assertThat(worker.runOnce()).as("30초가 지나기 전에는 다시 잡지 않음").isZero();

        clock.set(NOON.plusSeconds(30));
        worker.runOnce();
        clock.set(NOON.plusSeconds(60));
        worker.runOnce();

        assertThat(submissionStatus()).isEqualTo("graded");
        assertThat(((Number) jobRow().get("attempts")).intValue()).isEqualTo(2);
        assertThat(reader.calls()).isEqualTo(3);
    }

    @Test
    void 세_번_실패하면_작업_dead_제출_failed이고_점수_없음() {
        reader.willFail(ReadFailure.Kind.API_ERROR, 3);

        for (int i = 0; i < 3; i++) {
            clock.set(NOON.plusSeconds(30L * i));
            worker.runOnce();
        }

        assertThat(jobRow()).containsEntry("status", "dead");
        assertThat(((Number) jobRow().get("attempts")).intValue()).isEqualTo(3);
        assertThat(submissionRow())
                .containsEntry("status", "failed")
                .containsEntry("score", null)
                .containsEntry("graded_at", null);
        assertThat(jdbc.queryForObject(
                        "select count(*) from word_test_answer where submission_id = ?", Integer.class, submissionId))
                .isZero();
    }

    @Test
    void 문항_수가_다르게_읽히면_실패로_다시_한다() {
        reader.willRead(count -> List.of(new ReadItem(1, "beautiful", false)));

        worker.runOnce();

        assertThat(jobRow()).containsEntry("status", "queued");
        assertThat((String) jobRow().get("last_error")).startsWith("COUNT_MISMATCH");
    }

    @Test
    void 사진이_없거나_키가_비면_다시_하지_않고_failed() {
        photos.clear();
        worker.runOnce();

        assertThat(jobRow()).containsEntry("status", "dead");
        assertThat((String) jobRow().get("last_error")).startsWith("PHOTO_MISSING");
        assertThat(submissionStatus()).isEqualTo("failed");
        assertThat(reader.calls()).isZero();

        long noPhoto = submission(null);
        worker.runOnce();
        assertThat(jdbc.queryForObject("select status from word_test_submission where id = ?", String.class, noPhoto))
                .isEqualTo("failed");
    }

    @Test
    void 사진이_상한보다_크면_다시_하지_않고_failed() {
        photos.putSized(key, (int) SubmissionService.MAX_BYTES + 1, "image/jpeg");

        worker.runOnce();

        assertThat((String) jobRow().get("last_error")).startsWith("PHOTO_TOO_LARGE");
        assertThat(submissionStatus()).isEqualTo("failed");
        assertThat(reader.calls()).isZero();
    }

    @Test
    void 이미_graded인_제출은_다시_처리해도_그대로() {
        reader.willRead(count -> fullyCorrect());
        worker.runOnce();
        jdbc.update("update grading_job set status = 'queued' where id = ?", jobId);
        reader.willRead(count -> List.of(
                new ReadItem(1, "x", false),
                new ReadItem(2, "x", false),
                new ReadItem(3, "x", false),
                new ReadItem(4, "x", false)));

        worker.runOnce();

        assertThat(submissionRow()).containsEntry("status", "graded").containsEntry("score", 4);
        assertThat(jdbc.queryForList(
                        "select recognized_text from word_test_answer where submission_id = ?",
                        String.class,
                        submissionId))
                .doesNotContain("x");
        assertThat(jobStatus()).isEqualTo("done");
    }

    @Test
    void 읽는_동안에는_제출과_작업_행에_DB_잠금이_없다() {
        AtomicReference<String> seen = new AtomicReference<>();
        reader.duringRead(count -> seen.set(new TransactionTemplate(transactions).execute(status -> {
            // 다른 트랜잭션이 기다리지 않고(NOWAIT) 잠글 수 있어야 한다 — 워커가 잠금을 쥐고 있으면 즉시 오류
            jdbc.queryForObject(
                    "select id from word_test_submission where id = ? for update nowait", Long.class, submissionId);
            return jdbc.queryForObject(
                    "select status from grading_job where id = ? for update nowait", String.class, jobId);
        })));

        worker.runOnce();

        assertThat(seen.get()).isEqualTo("running");
        assertThat(submissionStatus()).isEqualTo("graded");
    }

    @Test
    void 읽기가_꺼져_있으면_작업을_잡지_않는다() {
        reader.enabled(false);

        assertThat(worker.runOnce()).isZero();
        assertThat(jobStatus()).isEqualTo("queued");
        assertThat(reader.calls()).isZero();
    }

    private List<ReadItem> fullyCorrect() {
        String[] answersInOrder = {"beautiful", "library", "receive", "atmosphere"};
        List<ReadItem> list = new ArrayList<>();
        for (int i = 0; i < answersInOrder.length; i++) {
            list.add(new ReadItem(i + 1, answersInOrder[i], false));
        }
        return list;
    }

    private long submission(String imageKey) {
        jdbc.update(
                "insert into word_test_submission (client_submission_id, student_id, lesson_id, section, attempt_no,"
                        + " image_key, status, total, created_at) values (?, ?, ?, 'B',"
                        + " ?, ?, 'pending', 4, '2026-11-26 03:00:00')",
                UUID.randomUUID().toString(),
                studentId,
                lessonId,
                jdbc.queryForObject(
                                "select count(*) from word_test_submission where student_id = ?",
                                Integer.class,
                                studentId)
                        + 1,
                imageKey);
        long id = jdbc.queryForObject("select last_insert_id()", Long.class);
        // 다른 테스트가 남긴 작업보다 먼저 잡히게 이른 시각
        jdbc.update(
                "insert into grading_job (submission_id, status, run_after) values (?, 'queued', '2000-01-01 00:00:00')",
                id);
        if (imageKey != null) {
            jobId = jdbc.queryForObject("select last_insert_id()", Long.class);
        }
        return id;
    }

    private Map<String, Object> submissionRow() {
        return jdbc.queryForMap(
                "select status, score, graded_at, model, prompt_version from word_test_submission where id = ?",
                submissionId);
    }

    private String submissionStatus() {
        return jdbc.queryForObject("select status from word_test_submission where id = ?", String.class, submissionId);
    }

    private Map<String, Object> jobRow() {
        return jdbc.queryForMap("select status, attempts, last_error from grading_job where id = ?", jobId);
    }

    private String jobStatus() {
        return (String) jobRow().get("status");
    }
}
