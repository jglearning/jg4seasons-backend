package com.jglearning.jg4seasons.wordtest.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.wordtest.grading.AnswerResult;
import com.jglearning.jg4seasons.wordtest.grading.GradedAnswer;
import com.jglearning.jg4seasons.wordtest.grading.Section;
import com.jglearning.jg4seasons.wordtest.grading.SubmissionStatus;
import com.jglearning.jg4seasons.wordtest.grading.UnsureReason;
import com.jglearning.jg4seasons.wordtest.persistence.SubmissionStore.GradingTarget;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 제출 상태 저장은 조건부 UPDATE 한 곳에서: pending → grading → graded | failed. 이미 바뀐 상태면 아무것도 바꾸지 않고 false. 답안은 (제출, 문항)으로 upsert
 */
@IntegrationTest
@Transactional
class SubmissionMarksTest {

    static final Instant AT = Instant.parse("2026-11-26T03:00:09.700Z");

    @Autowired
    SubmissionStore submissions;

    @Autowired
    AnswerStore answers;

    @Autowired
    JdbcTemplate jdbc;

    long lessonId;
    long studentId;
    long submissionId;
    long item1;
    long item2;
    int attempts;

    @BeforeEach
    void setUp() {
        TestData data = new TestData(jdbc);
        studentId = data.student(data.academy("상태 학원"), "상태 학생");
        lessonId = data.lessonId("L2-B1-01");
        submissionId = submission("pending", "homework/1/2/k.jpg");
        item1 = item(1);
        item2 = item(2);
    }

    @Test
    void 채점_대상은_회차_영역_사진_키_상태를_준다() {
        GradingTarget target = submissions.findForGrading(submissionId).orElseThrow();

        assertThat(target)
                .isEqualTo(new GradingTarget(
                        submissionId, lessonId, Section.B, "homework/1/2/k.jpg", SubmissionStatus.PENDING));
        long noPhoto = submission("pending", null);
        assertThat(submissions.findForGrading(noPhoto).orElseThrow().imageKey()).isNull();
        assertThat(submissions.findForGrading(-1)).isEmpty();
    }

    @Test
    void pending에서_grading_graded로_가고_점수_시각_모델을_남긴다() {
        assertThat(submissions.markGrading(submissionId)).isTrue();
        assertThat(status()).isEqualTo("grading");
        assertThat(submissions.markGrading(submissionId))
                .as("재시도 — 이미 grading이면 그대로 진행")
                .isTrue();

        assertThat(submissions.markGraded(submissionId, 7, AT, "model-x", "v1")).isTrue();

        assertThat(row())
                .containsEntry("status", "graded")
                .containsEntry("score", 7)
                .containsEntry("graded_at", LocalDateTime.of(2026, 11, 26, 3, 0, 9))
                .containsEntry("model", "model-x")
                .containsEntry("prompt_version", "v1");
    }

    @Test
    void 이미_graded면_다시_채점해도_바꾸지_않는다() {
        submissions.markGrading(submissionId);
        submissions.markGraded(submissionId, 7, AT, "model-x", "v1");

        assertThat(submissions.markGraded(submissionId, 1, AT.plusSeconds(60), "model-y", "v2"))
                .isFalse();
        assertThat(submissions.markGrading(submissionId)).isFalse();
        assertThat(submissions.markFailed(submissionId)).isFalse();
        assertThat(row())
                .containsEntry("score", 7)
                .containsEntry("model", "model-x")
                .containsEntry("status", "graded");
    }

    @Test
    void graded는_grading에서만() {
        assertThat(submissions.markGraded(submissionId, 1, AT, "m", "v1")).isFalse();
        assertThat(status()).isEqualTo("pending");
    }

    @Test
    void failed는_pending과_grading에서만이고_점수는_비어_있다() {
        assertThat(submissions.markFailed(submissionId)).isTrue();
        assertThat(row())
                .containsEntry("status", "failed")
                .containsEntry("score", null)
                .containsEntry("graded_at", null);

        long grading = submission("pending", "k");
        submissions.markGrading(grading);
        assertThat(submissions.markFailed(grading)).isTrue();
        assertThat(submissions.markGrading(grading)).isFalse();
    }

    @Test
    void 답안은_같은_제출을_두_번_저장해도_한_벌이고_뒤의_값() {
        answers.upsertAll(
                submissionId,
                List.of(
                        new GradedAnswer(item1, "beautful", AnswerResult.WRONG, null),
                        new GradedAnswer(item2, null, AnswerResult.WRONG, UnsureReason.UNREADABLE)));
        answers.upsertAll(
                submissionId,
                List.of(
                        new GradedAnswer(item1, "beautiful", AnswerResult.CORRECT, null),
                        new GradedAnswer(item2, null, AnswerResult.BLANK, null)));

        assertThat(jdbc.query(
                        "select item_id, recognized_text, unsure_reason, result from word_test_answer"
                                + " where submission_id = ? order by item_id",
                        (rs, n) -> rs.getLong("item_id") + " " + rs.getString("recognized_text") + " "
                                + rs.getString("unsure_reason") + " " + rs.getString("result"),
                        submissionId))
                .containsExactly(item1 + " beautiful null correct", item2 + " null null blank");
    }

    private String status() {
        return jdbc.queryForObject("select status from word_test_submission where id = ?", String.class, submissionId);
    }

    private Map<String, Object> row() {
        return jdbc.queryForMap(
                "select status, score, graded_at, model, prompt_version from word_test_submission where id = ?",
                submissionId);
    }

    private long submission(String status, String imageKey) {
        jdbc.update(
                "insert into word_test_submission (client_submission_id, student_id, lesson_id, section, attempt_no,"
                        + " image_key, status, total, created_at)"
                        + " values (?, ?, ?, 'B', ?, ?, ?, 2, '2026-11-26 03:00:00')",
                UUID.randomUUID().toString(),
                studentId,
                lessonId,
                ++attempts,
                imageKey,
                status);
        return jdbc.queryForObject("select last_insert_id()", Long.class);
    }

    private long item(int no) {
        jdbc.update(
                "insert into word_test_item (lesson_id, section, no, prompt, answer) values (?, 'B', ?, '뜻', 'word')",
                lessonId,
                no);
        return jdbc.queryForObject("select last_insert_id()", Long.class);
    }
}
