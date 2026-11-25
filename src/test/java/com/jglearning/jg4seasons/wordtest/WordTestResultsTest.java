package com.jglearning.jg4seasons.wordtest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.auth.api.AcademyStudents;
import com.jglearning.jg4seasons.auth.api.StudentAccounts;
import com.jglearning.jg4seasons.infra.storage.MemoryPhotoStorage;
import com.jglearning.jg4seasons.infra.web.error.ApiException;
import com.jglearning.jg4seasons.infra.web.error.CommonErrorCode;
import com.jglearning.jg4seasons.wordtest.api.WordTestResults;
import com.jglearning.jg4seasons.wordtest.api.WordTestResults.Detail;
import com.jglearning.jg4seasons.wordtest.api.WordTestResults.Item;
import com.jglearning.jg4seasons.wordtest.api.WordTestResults.Summary;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** 교사 조회용 결과 api: 학원 경계는 학생 묶음(AcademyStudents)만. 1회차 B 문항 4개(지어낸 것) */
@IntegrationTest
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class WordTestResultsTest {

    static final String CODE = "L2-B1-01";

    @Autowired
    WordTestResults results;

    @Autowired
    StudentAccounts accounts;

    @Autowired
    MemoryPhotoStorage photos;

    @Autowired
    JdbcTemplate jdbc;

    TestData data;
    long academyId;
    long lessonId;
    long studentA;
    long studentB;
    long studentC;
    long[] items = new long[5];

    @BeforeEach
    void setUp() {
        data = new TestData(jdbc);
        academyId = data.academy("결과 학원");
        jdbc.update(
                "update academy set settings = '{\"timezone\":\"Asia/Seoul\",\"features\":{\"wordTest\":true}}' where id = ?",
                academyId);
        studentA = data.student(academyId, "가학생");
        studentB = data.student(academyId, "나학생");
        studentC = data.student(academyId, "다학생");
        lessonId = data.lessonId(CODE);
        String[][] rows = {{"아름다운", "beautiful"}, {"도서관", "library"}, {"받다", "receive"}, {"분위기", "atmosphere"}};
        for (int no = 1; no <= 4; no++) {
            jdbc.update(
                    "insert into word_test_item (lesson_id, section, no, prompt, answer) values (?, 'B', ?, ?, ?)",
                    lessonId,
                    no,
                    rows[no - 1][0],
                    rows[no - 1][1]);
            items[no] = jdbc.queryForObject("select last_insert_id()", Long.class);
        }
    }

    @Test
    void 최근_제출은_학생마다_마지막_시도() {
        // 시도 2를 먼저 넣는다 — id 순서가 아니라 시도 번호로 마지막을 고르는지
        long a2 = submission(studentA, lessonId, "B", 2, "pending", null, "homework/a2.jpg");
        submission(studentA, lessonId, "B", 1, "graded", 2, "homework/a1.jpg");
        long b1 = submission(studentB, lessonId, "B", 1, "graded", 3, "homework/b1.jpg");

        Map<Long, Summary> latest = results.latestOf(students(studentA, studentB, studentC), lessonId, "B");

        assertThat(latest).containsOnlyKeys(studentA, studentB);
        assertThat(latest.get(studentA))
                .isEqualTo(new Summary(a2, "pending", null, 4, 2, Instant.parse("2026-11-26T03:00:00Z")));
        assertThat(latest.get(studentB).id()).isEqualTo(b1);
        assertThat(latest.get(studentB).score()).isEqualTo(3);
    }

    @Test
    void graded가_아닌_최근_제출은_점수가_null() {
        submission(studentA, lessonId, "B", 1, "grading", 2, "homework/a.jpg");

        assertThat(results.latestOf(students(studentA), lessonId, "B")
                        .get(studentA)
                        .score())
                .isNull();
    }

    @Test
    void 다른_회차_영역과_묶음_밖_학생의_제출은_섞이지_않는다() {
        long other =
                jdbc.queryForObject("select id from lesson where id <> ? order by id limit 1", Long.class, lessonId);
        submission(studentA, other, "B", 1, "graded", 1, "homework/x.jpg");
        submission(studentA, lessonId, "C", 1, "graded", 1, "homework/y.jpg");
        submission(studentB, lessonId, "B", 1, "graded", 1, "homework/z.jpg");

        assertThat(results.latestOf(students(studentA), lessonId, "B")).isEmpty();
        assertThat(results.latestOf(students(), lessonId, "B")).isEmpty();
    }

    @Test
    void 묶음_밖_학생의_제출은_상세도_없다() {
        long otherAcademy = data.academy("다른 학원");
        long stranger = data.student(otherAcademy, "남학생");
        long theirs = submission(stranger, lessonId, "B", 1, "graded", 1, "homework/s.jpg");
        long mineB = submission(studentB, lessonId, "B", 1, "graded", 1, "homework/b.jpg");

        assertThat(results.detailOf(students(studentA, studentB), theirs)).isEmpty();
        assertThat(results.detailOf(students(studentA), mineB)).isEmpty();
        assertThat(results.detailOf(students(studentA), -1)).isEmpty();
    }

    @Test
    void graded_상세는_번호_순_문항별_결과이고_unclear에도_정답() {
        long id = submission(studentA, lessonId, "B", 1, "graded", 1, "homework/a.jpg");
        jdbc.update("update word_test_submission set graded_at = '2026-11-26 03:00:09' where id = ?", id);
        answer(id, items[4], null, "unreadable", "unclear");
        answer(id, items[1], "beautful", null, "wrong");
        answer(id, items[2], "library", null, "correct");
        answer(id, items[3], null, null, "blank");

        Detail detail = results.detailOf(students(studentA), id).orElseThrow();

        assertThat(detail.studentId()).isEqualTo(studentA);
        assertThat(detail.lessonCode()).isEqualTo(CODE);
        assertThat(detail.section()).isEqualTo("B");
        assertThat(detail.status()).isEqualTo("graded");
        assertThat(detail.score()).isEqualTo(1);
        assertThat(detail.total()).isEqualTo(4);
        assertThat(detail.gradedAt()).isEqualTo(Instant.parse("2026-11-26T03:00:09Z"));
        assertThat(detail.items())
                .containsExactly(
                        new Item(1, "아름다운", "beautful", "wrong", "beautiful"),
                        new Item(2, "도서관", "library", "correct", "library"),
                        new Item(3, "받다", "", "blank", "receive"),
                        new Item(4, "분위기", "", "unclear", "atmosphere"));
    }

    @Test
    void 채점된_제출에_답안이_빠진_문항은_빼고_WARN을_남긴다(CapturedOutput output) {
        long id = submission(studentA, lessonId, "B", 1, "graded", 1, "homework/a.jpg");
        answer(id, items[1], "beautful", null, "wrong");
        answer(id, items[2], "library", null, "correct");
        answer(id, items[3], null, null, "blank");

        Detail detail = results.detailOf(students(studentA), id).orElseThrow();

        assertThat(detail.items()).extracting(Item::no).containsExactly(1, 2, 3);
        assertThat(output.getOut())
                .contains("WARN")
                .contains("채점된 제출의 답안 수가 문항과 다르다: submission " + id)
                .doesNotContain("가학생");
    }

    @Test
    void graded가_아니면_items와_점수_채점_시각이_없다() {
        long id = submission(studentA, lessonId, "B", 1, "grading", 2, "homework/a.jpg");

        Detail detail = results.detailOf(students(studentA), id).orElseThrow();

        assertThat(detail.items()).isNull();
        assertThat(detail.score()).isNull();
        assertThat(detail.gradedAt()).isNull();
    }

    @Test
    void 사진_주소는_서명_주소이고_키가_없으면_null() {
        long withPhoto = submission(studentA, lessonId, "B", 1, "graded", 1, "homework/a.jpg");
        long noPhoto = submission(studentA, lessonId, "B", 2, "failed", null, null);

        assertThat(results.detailOf(students(studentA), withPhoto).orElseThrow().imageUrl())
                .isEqualTo("https://photos.test/homework/a.jpg?view");
        assertThat(results.detailOf(students(studentA), noPhoto).orElseThrow().imageUrl())
                .isNull();
    }

    @Test
    void 사진_보기_주소는_5분이고_저장소가_주소를_주지_않으면_null() {
        long id = submission(studentA, lessonId, "B", 1, "graded", 1, "homework/a.jpg");
        try {
            results.detailOf(students(studentA), id);
            assertThat(photos.lastViewTtl()).isEqualTo(Duration.ofMinutes(5));

            photos.viewUrls(false);
            assertThat(results.detailOf(students(studentA), id).orElseThrow().imageUrl())
                    .isNull();
        } finally {
            photos.clear();
        }
    }

    @Test
    void 기능이_꺼진_학원은_FEATURE_DISABLED이고_시험지_유무를_안다() {
        long off = data.academy("꺼진 학원");

        assertThatThrownBy(() -> results.requireEnabled(off))
                .isInstanceOfSatisfying(
                        ApiException.class, e -> assertThat(e.errorCode()).isEqualTo(CommonErrorCode.FEATURE_DISABLED));
        results.requireEnabled(academyId);
        assertThat(results.hasItems(lessonId, "B")).isTrue();
        assertThat(results.hasItems(lessonId, "C")).isFalse();
    }

    private AcademyStudents students(Long... ids) {
        return accounts.roster(academyId, List.of(ids)).students();
    }

    private long submission(
            long student, long lesson, String section, int attempt, String status, Integer score, String key) {
        jdbc.update(
                "insert into word_test_submission (client_submission_id, student_id, lesson_id, section, attempt_no,"
                        + " image_key, status, score, total, created_at) values (?, ?, ?, ?, ?, ?, ?, ?, 4, ?)",
                UUID.randomUUID().toString(),
                student,
                lesson,
                section,
                attempt,
                key,
                status,
                score,
                LocalDateTime.of(2026, 11, 26, 3, 0));
        return jdbc.queryForObject("select last_insert_id()", Long.class);
    }

    private void answer(long submission, long item, String text, String reason, String result) {
        jdbc.update(
                "insert into word_test_answer (submission_id, item_id, recognized_text, unsure_reason, result)"
                        + " values (?, ?, ?, ?, ?)",
                submission,
                item,
                text,
                reason,
                result);
    }
}
