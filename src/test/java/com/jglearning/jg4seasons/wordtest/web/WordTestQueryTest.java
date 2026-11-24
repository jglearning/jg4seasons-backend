package com.jglearning.jg4seasons.wordtest.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.MutableClock;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.auth.AuthCookieSupport;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * D-3 GET /word-tests/submissions/{id}, D-4 GET /word-tests/lessons/{code}/latest?section=. 제출·답안은 jdbc로 넣는다(채점은 다음 이슈). 오늘 = 서울
 * 2026-11-26, 1회차 B 문항 4개(지어낸 것)
 */
@IntegrationTest
@Transactional
class WordTestQueryTest {

    static final String TODAY = "L2-B1-01";
    static final Instant NOON = Instant.parse("2026-11-26T03:00:00Z");

    @Autowired
    MockMvcTester mvc;

    @Autowired
    AuthCookieSupport cookies;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MutableClock clock;

    TestData data;
    long academyId;
    long classId;
    long studentId;
    long lessonId;
    long[] items = new long[5];

    @BeforeEach
    void setUp() {
        clock.set(NOON);
        data = new TestData(jdbc);
        academyId = data.academy("조회 학원");
        WordTestUploadTest.enableWordTest(jdbc, academyId);
        classId = data.clazz(academyId, "L2-B1", "겨울 그린 A반", TestData.uniqueAccessCode(), "active");
        studentId = data.student(academyId, "김학생");
        data.enroll(classId, studentId);
        data.session(classId, 1, TODAY, LocalDate.of(2026, 11, 26), "scheduled");
        lessonId = data.lessonId(TODAY);
        String[][] rows = {{"아름다운", "beautiful"}, {"도서관", "library"}, {"받다", "receive"}, {"분위기", "atmosphere"}};
        // 번호 순과 넣는 순서를 달리해 정렬을 확인
        for (int no : new int[] {3, 1, 4, 2}) {
            jdbc.update(
                    "insert into word_test_item (lesson_id, section, no, prompt, answer) values (?, 'B', ?, ?, ?)",
                    lessonId,
                    no,
                    rows[no - 1][0],
                    rows[no - 1][1]);
            items[no] = jdbc.queryForObject("select last_insert_id()", Long.class);
        }
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    @Test
    void 채점_전에는_items_칸이_없고_score_gradedAt은_null() {
        long id = submission(studentId, 1, "pending", null, "2026-11-26T03:00:00");

        MvcTestResult result = get(student(), "/api/v1/word-tests/submissions/" + id);

        assertThat(result).hasStatusOk();
        var json = assertThat(result).bodyJson();
        json.extractingPath("$.id").isEqualTo((int) id);
        json.extractingPath("$.lessonCode").isEqualTo(TODAY);
        json.extractingPath("$.section").isEqualTo("B");
        json.extractingPath("$.attemptNo").isEqualTo(1);
        json.extractingPath("$.status").isEqualTo("pending");
        json.extractingPath("$.total").isEqualTo(4);
        json.extractingPath("$.submittedAt").isEqualTo("2026-11-26T03:00:00Z");
        json.hasPathSatisfying("$.score", value -> assertThat(value).isNull());
        json.hasPathSatisfying("$.gradedAt", value -> assertThat(value).isNull());
        json.doesNotHavePath("$.items");
    }

    @Test
    void 채점되면_문항별_뜻_쓴_글자_결과_정답을_번호_순으로() {
        long id = submission(studentId, 1, "graded", 1, "2026-11-26T03:00:00");
        jdbc.update("update word_test_submission set graded_at = '2026-11-26 03:00:09' where id = ?", id);
        answer(id, items[1], "beautful", null, "wrong");
        answer(id, items[2], "library", null, "correct");
        answer(id, items[3], null, null, "blank");
        answer(id, items[4], "atm", "unreadable", "wrong");

        MvcTestResult result = get(student(), "/api/v1/word-tests/submissions/" + id);

        assertThat(result).hasStatusOk();
        var json = assertThat(result).bodyJson();
        json.extractingPath("$.score").isEqualTo(1);
        json.extractingPath("$.gradedAt").isEqualTo("2026-11-26T03:00:09Z");
        json.extractingPath("$.items[*].no").isEqualTo(List.of(1, 2, 3, 4));
        json.extractingPath("$.items[*].prompt").isEqualTo(List.of("아름다운", "도서관", "받다", "분위기"));
        json.extractingPath("$.items[*].written").isEqualTo(List.of("beautful", "library", "", "atm"));
        json.extractingPath("$.items[*].result").isEqualTo(List.of("wrong", "correct", "blank", "unclear"));
        json.extractingPath("$.items[*].answer").isEqualTo(List.of("beautiful", "library", "receive", "atmosphere"));
    }

    @Test
    void 다른_학생의_제출은_404() {
        long other = data.student(academyId, "이학생");
        long id = submission(other, 1, "pending", null, "2026-11-26T03:00:00");

        assertProblem(get(student(), "/api/v1/word-tests/submissions/" + id), HttpStatus.NOT_FOUND, "NOT_FOUND");
    }

    @Test
    void 다른_학원_학생의_제출은_404() {
        long otherAcademy = data.academy("다른 학원");
        WordTestUploadTest.enableWordTest(jdbc, otherAcademy);
        long outsider = data.student(otherAcademy, "밖학생");
        long mine = submission(studentId, 1, "graded", 0, "2026-11-26T03:00:00");

        assertProblem(
                get(cookies.studentAccess(outsider, otherAcademy), "/api/v1/word-tests/submissions/" + mine),
                HttpStatus.NOT_FOUND,
                "NOT_FOUND");
    }

    @Test
    void 없는_제출은_404() {
        assertProblem(get(student(), "/api/v1/word-tests/submissions/999999999"), HttpStatus.NOT_FOUND, "NOT_FOUND");
    }

    @Test
    void 기능이_꺼지면_제출_조회도_최근_제출도_404_FEATURE_DISABLED() {
        long id = submission(studentId, 1, "pending", null, "2026-11-26T03:00:00");
        jdbc.update("update academy set settings = null where id = ?", academyId);

        assertProblem(get(student(), "/api/v1/word-tests/submissions/" + id), HttpStatus.NOT_FOUND, "FEATURE_DISABLED");
        assertProblem(get(student(), latest(TODAY, "B")), HttpStatus.NOT_FOUND, "FEATURE_DISABLED");
    }

    @Test
    void 최근_제출이_없으면_204() {
        assertThat(get(student(), latest(TODAY, "B"))).hasStatus(HttpStatus.NO_CONTENT);
    }

    @Test
    void 최근_제출은_가장_늦은_시도이고_영역과_학생을_가린다() {
        submission(studentId, 1, "failed", null, "2026-11-26T02:00:00");
        long last = submission(studentId, 2, "pending", null, "2026-11-26T02:30:00");
        jdbc.update(
                "insert into word_test_item (lesson_id, section, no, prompt, answer) values (?, 'A', 1, 'word', '뜻')",
                lessonId);
        long other = data.student(academyId, "이학생");
        submission(other, 3, "pending", null, "2026-11-26T02:59:00");

        MvcTestResult result = get(student(), latest(TODAY, "B"));

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.id").isEqualTo((int) last);
        assertThat(result).bodyJson().extractingPath("$.attemptNo").isEqualTo(2);
        assertThat(get(student(), latest(TODAY, "A"))).hasStatus(HttpStatus.NO_CONTENT);
    }

    @Test
    void 최근_제출에서_문항이_없는_영역은_404_모르는_영역은_400() {
        assertProblem(get(student(), latest(TODAY, "C")), HttpStatus.NOT_FOUND, "NOT_FOUND");
        assertProblem(get(student(), latest(TODAY, "E")), HttpStatus.BAD_REQUEST, "VALIDATION");
        assertProblem(
                get(student(), "/api/v1/word-tests/lessons/" + TODAY + "/latest"),
                HttpStatus.BAD_REQUEST,
                "VALIDATION");
    }

    @Test
    void 최근_제출은_수업일_전이면_423() {
        data.session(classId, 2, "L2-B1-02", LocalDate.of(2026, 11, 27), "scheduled");

        assertProblem(get(student(), latest("L2-B1-02", "B")), HttpStatus.LOCKED, "LESSON_LOCKED");
    }

    private static String latest(String code, String section) {
        return "/api/v1/word-tests/lessons/" + code + "/latest?section=" + section;
    }

    private long submission(long student, int attempt, String status, Integer score, String createdAtUtc) {
        jdbc.update(
                "insert into word_test_submission (client_submission_id, student_id, lesson_id, section, attempt_no,"
                        + " image_key, status, score, total, created_at) values (?, ?, ?, 'B', ?, 'homework/k.jpg', ?, ?, 4, ?)",
                UUID.randomUUID().toString(),
                student,
                lessonId,
                attempt,
                status,
                score,
                LocalDateTime.parse(createdAtUtc));
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

    private Cookie student() {
        return cookies.studentAccess(studentId, academyId);
    }

    private MvcTestResult get(Cookie access, String uri) {
        return mvc.get().uri(uri).cookie(access).exchange();
    }

    private static void assertProblem(MvcTestResult result, HttpStatus status, String code) {
        assertThat(result).hasStatus(status);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo(code);
    }
}
