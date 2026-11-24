package com.jglearning.jg4seasons.wordtest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.wordtest.grading.AnswerResult;
import com.jglearning.jg4seasons.wordtest.grading.Section;
import com.jglearning.jg4seasons.wordtest.grading.SubmissionStatus;
import com.jglearning.jg4seasons.wordtest.grading.UnsureReason;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** V12 단어시험 표의 DB 제약: 값 목록(CHECK, 대소문자 구분)과 겹침 금지(UNIQUE). 문항은 지어낸 것 */
@IntegrationTest
@Transactional
class WordTestSchemaTest {

    static final String LESSON = "L1-B1-01";

    @Autowired
    JdbcTemplate jdbc;

    TestData data;
    long lessonId;
    long studentId;

    @BeforeEach
    void setUp() {
        data = new TestData(jdbc);
        lessonId = data.lessonId(LESSON);
        studentId = data.student(data.academy("시험 학원"), "학생");
    }

    @Test
    void 문항은_회차_영역_번호가_겹치면_거부한다() {
        item("B", 1);
        item("A", 1);

        violates(() -> item("B", 1), "uk_word_test_item_lesson_section_no");
    }

    @Test
    void 모르는_영역과_소문자_영역은_거부한다() {
        violates(() -> item("E", 1), "ck_word_test_item_section");
        violates(() -> item("b", 1), "ck_word_test_item_section");
    }

    @Test
    void 허용_답은_문항마다_겹치지_않는다() {
        long item = item("B", 1);
        accepted(item, "judgement");

        violates(() -> accepted(item, "judgement"), "uk_word_test_accepted_answer_item_text");
    }

    @Test
    void 제출_상태는_여섯_값만_받는다() {
        int attempt = 1;
        for (String status : new String[] {"pending", "grading", "confirming", "graded", "rejected", "failed"}) {
            submission("B", attempt++, status);
        }

        violates(() -> submission("B", 7, "done"), "ck_word_test_submission_status");
        violates(() -> submission("B", 8, "GRADED"), "ck_word_test_submission_status");
    }

    @Test
    void 제출_기본값은_학생_수정_0과_이의_없음이다() {
        long id = submission("B", 1, "pending");

        assertThat(jdbc.queryForMap(
                        "select student_edit_count, disputed, score, graded_at from word_test_submission where id = ?",
                        id))
                .containsEntry("student_edit_count", 0)
                .containsEntry("disputed", false)
                .containsEntry("score", null)
                .containsEntry("graded_at", null);
    }

    @Test
    void 같은_학생_회차_영역의_같은_시도_번호는_거부한다() {
        submission("B", 1, "pending");
        submission("A", 1, "pending");

        violates(() -> submission("B", 1, "pending"), "uk_word_test_submission_attempt");
    }

    @Test
    void 같은_client_submission_id는_거부한다() {
        String clientId = UUID.randomUUID().toString();
        submission(clientId, "B", 1, "pending");

        violates(() -> submission(clientId, "B", 2, "pending"), "uk_word_test_submission_client");
    }

    @Test
    void 답안은_제출당_문항_하나이고_결과와_사유는_정한_값만() {
        long submission = submission("B", 1, "graded");
        long first = item("B", 1);
        long second = item("B", 2);
        answer(submission, first, "correct", null);
        answer(submission, second, "unclear", "unreadable");

        violates(() -> answer(submission, first, "wrong", null), "uk_word_test_answer_submission_item");
        long third = item("B", 3);
        violates(() -> answer(submission, third, "maybe", null), "ck_word_test_answer_result");
        violates(() -> answer(submission, third, "wrong", "guess"), "ck_word_test_answer_unsure");
        violates(() -> answer(submission, third, "CORRECT", null), "ck_word_test_answer_result");
        // 판정 규칙(코드의 Judgement와 같음): unclear는 이유 필수, unreadable은 unclear와만
        violates(() -> answer(submission, third, "unclear", null), "ck_word_test_answer_unclear_reason");
        violates(() -> answer(submission, third, "wrong", "unreadable"), "ck_word_test_answer_unreadable");
    }

    @Test
    void 교사_정정_결과도_정한_값만() {
        long submission = submission("B", 1, "graded");
        long item = item("B", 1);
        long answer = answer(submission, item, "wrong", null);

        jdbc.update("update word_test_answer set teacher_result = 'correct' where id = ?", answer);
        violates(
                () -> jdbc.update("update word_test_answer set teacher_result = 'ok' where id = ?", answer),
                "ck_word_test_answer_teacher_result");
        // 교사 정정은 판정을 내리는 것이라 unclear가 없다
        violates(
                () -> jdbc.update("update word_test_answer set teacher_result = 'unclear' where id = ?", answer),
                "ck_word_test_answer_teacher_result");
    }

    @Test
    void 자바_값_목록은_모두_CHECK를_통과한다() {
        // wordtest.grading의 dbValue()가 V12 CHECK 목록과 어긋나면 다음 PR의 저장에서 처음 터진다 — 여기서 미리 대 본다
        int attempt = 1;
        for (SubmissionStatus status : SubmissionStatus.values()) {
            submission("B", attempt++, status.dbValue());
        }
        long submission = submission("B", attempt, "graded");
        int no = 1;
        for (Section section : Section.values()) {
            item(section.dbValue(), 1);
            submission(section.dbValue(), 100, "pending");
        }
        for (AnswerResult result : AnswerResult.values()) {
            // unclear는 이유가 있어야 한다(ck_word_test_answer_unclear_reason)
            String reason = result == AnswerResult.UNCLEAR ? UnsureReason.UNREADABLE.dbValue() : null;
            long answer = answer(submission, item("B", ++no), result.dbValue(), reason);
            if (result != AnswerResult.UNCLEAR) {
                jdbc.update("update word_test_answer set teacher_result = ? where id = ?", result.dbValue(), answer);
            }
        }
        for (UnsureReason reason : UnsureReason.values()) {
            answer(
                    submission,
                    item("B", ++no),
                    reason == UnsureReason.UNREADABLE ? "unclear" : "wrong",
                    reason.dbValue());
        }
    }

    @Test
    void 제출_영역과_번호_시도는_범위를_지킨다() {
        violates(() -> submission("E", 1, "pending"), "ck_word_test_submission_section");
        violates(() -> submission("B", 0, "pending"), "ck_word_test_submission_attempt");
        violates(() -> item("B", 0), "ck_word_test_item_no");
    }

    @Test
    void 작업은_제출당_하나이고_상태는_넷() {
        long submission = submission("B", 1, "pending");
        long job = job(submission, "queued");
        for (String status : new String[] {"running", "done", "dead"}) {
            jdbc.update("update grading_job set status = ? where id = ?", status, job);
        }
        assertThat(jdbc.queryForObject("select attempts from grading_job where id = ?", Integer.class, job))
                .isZero();

        violates(() -> job(submission, "queued"), "uk_grading_job_submission");
        long other = submission("B", 2, "pending");
        violates(() -> job(other, "failed"), "ck_grading_job_status");
    }

    /** MySQL CHECK 위반(3819)은 Spring이 분류하지 않아(UncategorizedSQLException) UNIQUE와 함께 제약 이름으로 확인 */
    private static void violates(ThrowingCallable insert, String constraint) {
        assertThatThrownBy(insert).isInstanceOf(DataAccessException.class).hasMessageContaining(constraint);
    }

    private long job(long submissionId, String status) {
        jdbc.update(
                "insert into grading_job (submission_id, status, run_after) values (?, ?, now(3))",
                submissionId,
                status);
        return lastId();
    }

    private long item(String section, int no) {
        jdbc.update(
                "insert into word_test_item (lesson_id, section, no, prompt, answer) values (?, ?, ?, '보기 뜻', 'sample')",
                lessonId,
                section,
                no);
        return lastId();
    }

    private void accepted(long itemId, String text) {
        jdbc.update("insert into word_test_accepted_answer (item_id, text) values (?, ?)", itemId, text);
    }

    private long submission(String section, int attempt, String status) {
        return submission(UUID.randomUUID().toString(), section, attempt, status);
    }

    private long submission(String clientId, String section, int attempt, String status) {
        jdbc.update(
                "insert into word_test_submission (client_submission_id, student_id, lesson_id, section, attempt_no,"
                        + " image_key, status, total) values (?, ?, ?, ?, ?, 'homework/1/1/x.jpg', ?, 48)",
                clientId,
                studentId,
                lessonId,
                section,
                attempt,
                status);
        return lastId();
    }

    private long answer(long submissionId, long itemId, String result, String unsureReason) {
        jdbc.update(
                "insert into word_test_answer (submission_id, item_id, recognized_text, unsure_reason, result)"
                        + " values (?, ?, 'sample', ?, ?)",
                submissionId,
                itemId,
                unsureReason,
                result);
        return lastId();
    }

    private long lastId() {
        return jdbc.queryForObject("select last_insert_id()", Long.class);
    }
}
