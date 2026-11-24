package com.jglearning.jg4seasons.wordtest.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.MutableClock;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.auth.AuthCookieSupport;
import com.jglearning.jg4seasons.infra.storage.MemoryPhotoStorage;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * D-2 POST /word-tests/submissions — 제출 행과 채점 작업을 한 트랜잭션으로. 같은 clientSubmissionId면 기존 제출. 오늘 = 서울 2026-11-26 12:00, 반은 L2-B1,
 * 1회차 오늘·2회차 내일. 1회차 B 문항 3개(지어낸 것). 사진은 메모리 저장소에 넣어 둔다
 */
@IntegrationTest
@Transactional
class WordTestSubmitTest {

    static final String TODAY = "L2-B1-01";
    static final String TOMORROW = "L2-B1-02";
    static final Instant NOON = Instant.parse("2026-11-26T03:00:00Z");
    static final String URL = "/api/v1/word-tests/submissions";
    static final int FIVE_MB = 5 * 1024 * 1024;

    @Autowired
    MockMvcTester mvc;

    @Autowired
    AuthCookieSupport cookies;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MutableClock clock;

    @Autowired
    MemoryPhotoStorage photos;

    TestData data;
    long academyId;
    long classId;
    long studentId;
    long lessonId;

    @BeforeEach
    void setUp() {
        clock.set(NOON);
        data = new TestData(jdbc);
        academyId = data.academy("단어시험 학원");
        WordTestUploadTest.enableWordTest(jdbc, academyId);
        classId = data.clazz(academyId, "L2-B1", "겨울 그린 A반", TestData.uniqueAccessCode(), "active");
        studentId = data.student(academyId, "김학생");
        data.enroll(classId, studentId);
        data.session(classId, 1, TODAY, LocalDate.of(2026, 11, 26), "scheduled");
        data.session(classId, 2, TOMORROW, LocalDate.of(2026, 11, 27), "scheduled");
        lessonId = data.lessonId(TODAY);
        for (int no = 1; no <= 3; no++) {
            jdbc.update(
                    "insert into word_test_item (lesson_id, section, no, prompt, answer) values (?, 'B', ?, '뜻', 'word')",
                    lessonId,
                    no);
        }
    }

    @AfterEach
    void tearDown() {
        clock.reset();
        photos.clear();
    }

    @Test
    void 제출하면_202와_pending이고_제출과_작업이_하나씩() {
        String clientId = UUID.randomUUID().toString();

        MvcTestResult result = submit(student(), clientId, TODAY, "B", photo());

        assertThat(result).hasStatus(HttpStatus.ACCEPTED);
        long id = ((Number) assertThat(result).bodyJson().extractingPath("$.id").actual()).longValue();
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("pending");
        Map<String, Object> row = jdbc.queryForMap(
                "select client_submission_id, student_id, lesson_id, section, attempt_no, status, score, total,"
                        + " model, created_at from word_test_submission where id = ?",
                id);
        assertThat(row)
                .containsEntry("client_submission_id", clientId)
                .containsEntry("student_id", studentId)
                .containsEntry("lesson_id", lessonId)
                .containsEntry("section", "B")
                .containsEntry("status", "pending")
                .containsEntry("score", null)
                .containsEntry("total", 3)
                .containsEntry("model", null)
                .containsEntry("created_at", LocalDateTime.of(2026, 11, 26, 3, 0));
        assertThat(((Number) row.get("attempt_no")).intValue()).isEqualTo(1);
        assertThat(jdbc.queryForMap("select status, attempts, run_after from grading_job where submission_id = ?", id))
                .containsEntry("status", "queued")
                .containsEntry("run_after", LocalDateTime.of(2026, 11, 26, 3, 0));
    }

    @Test
    void 같은_clientSubmissionId면_200과_같은_id이고_행이_늘지_않는다() {
        String clientId = UUID.randomUUID().toString();
        String key = photo();
        MvcTestResult first = submit(student(), clientId, TODAY, "B", key);

        MvcTestResult again = submit(student(), clientId, TODAY, "B", key);

        assertThat(again).hasStatusOk();
        assertThat(again)
                .bodyJson()
                .extractingPath("$.id")
                .isEqualTo(assertThat(first).bodyJson().extractingPath("$.id").actual());
        assertThat(count("word_test_submission")).isEqualTo(1);
        assertThat(count("grading_job")).isEqualTo(1);
    }

    @Test
    void 다른_학생의_clientSubmissionId면_400_VALIDATION() {
        String clientId = UUID.randomUUID().toString();
        submit(student(), clientId, TODAY, "B", photo());
        long other = data.student(academyId, "이학생");
        data.enroll(classId, other);

        MvcTestResult result = submit(cookies.studentAccess(other, academyId), clientId, TODAY, "B", photo(other));

        assertProblem(result, HttpStatus.BAD_REQUEST, "VALIDATION");
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("clientSubmissionId");
    }

    @Test
    void 남의_imageKey는_400_INVALID_IMAGE이고_행이_없다() {
        long other = data.student(academyId, "이학생");

        assertProblem(
                submit(student(), UUID.randomUUID().toString(), TODAY, "B", photo(other)),
                HttpStatus.BAD_REQUEST,
                "INVALID_IMAGE");
        assertThat(count("word_test_submission")).isZero();
    }

    @Test
    void 올라가지_않은_사진은_400_INVALID_IMAGE() {
        String key = "homework/" + academyId + "/" + studentId + "/" + UUID.randomUUID() + ".jpg";

        assertProblem(
                submit(student(), UUID.randomUUID().toString(), TODAY, "B", key),
                HttpStatus.BAD_REQUEST,
                "INVALID_IMAGE");
    }

    @Test
    void 사진은_5MB까지() {
        String exact = key();
        photos.putSized(exact, FIVE_MB, "image/jpeg");
        String over = key();
        photos.putSized(over, FIVE_MB + 1, "image/jpeg");

        assertThat(submit(student(), UUID.randomUUID().toString(), TODAY, "B", exact))
                .hasStatus(HttpStatus.ACCEPTED);
        assertProblem(
                submit(student(), UUID.randomUUID().toString(), TODAY, "B", over),
                HttpStatus.BAD_REQUEST,
                "INVALID_IMAGE");
    }

    @Test
    void 올라간_형식이_JPEG_PNG가_아니면_400_INVALID_IMAGE() {
        String key = key();
        photos.put(key, new byte[] {1}, "image/heic");

        assertProblem(
                submit(student(), UUID.randomUUID().toString(), TODAY, "B", key),
                HttpStatus.BAD_REQUEST,
                "INVALID_IMAGE");
    }

    @Test
    void 문항이_없는_영역은_404_NOT_FOUND() {
        assertProblem(
                submit(student(), UUID.randomUUID().toString(), TODAY, "C", photo()),
                HttpStatus.NOT_FOUND,
                "NOT_FOUND");
    }

    @Test
    void 모르는_영역은_400_VALIDATION() {
        assertProblem(
                submit(student(), UUID.randomUUID().toString(), TODAY, "E", photo()),
                HttpStatus.BAD_REQUEST,
                "VALIDATION");
    }

    @Test
    void clientSubmissionId가_UUID가_아니면_400_VALIDATION() {
        assertProblem(submit(student(), "not-a-uuid", TODAY, "B", photo()), HttpStatus.BAD_REQUEST, "VALIDATION");
    }

    @Test
    void 기능이_꺼지면_404_FEATURE_DISABLED() {
        jdbc.update("update academy set settings = null where id = ?", academyId);

        assertProblem(
                submit(student(), UUID.randomUUID().toString(), TODAY, "B", photo()),
                HttpStatus.NOT_FOUND,
                "FEATURE_DISABLED");
    }

    @Test
    void 수업일_전_회차는_423_LESSON_LOCKED() {
        assertProblem(
                submit(student(), UUID.randomUUID().toString(), TOMORROW, "B", photo()),
                HttpStatus.LOCKED,
                "LESSON_LOCKED");
    }

    @Test
    void 하루_3회를_넘으면_429_DAILY_LIMIT이고_다른_영역은_따로_센다() {
        for (int i = 0; i < 3; i++) {
            assertThat(submit(student(), UUID.randomUUID().toString(), TODAY, "B", photo()))
                    .hasStatus(HttpStatus.ACCEPTED);
        }

        assertProblem(
                submit(student(), UUID.randomUUID().toString(), TODAY, "B", photo()),
                HttpStatus.TOO_MANY_REQUESTS,
                "DAILY_LIMIT");
        jdbc.update(
                "insert into word_test_item (lesson_id, section, no, prompt, answer) values (?, 'A', 1, 'word', '뜻')",
                lessonId);
        assertThat(submit(student(), UUID.randomUUID().toString(), TODAY, "A", photo()))
                .hasStatus(HttpStatus.ACCEPTED);
        assertThat(count("word_test_submission")).isEqualTo(4);
    }

    @Test
    void 학원_날짜가_바뀌면_다시_낼_수_있고_시도_번호는_이어진다() {
        clock.set(Instant.parse("2026-11-26T14:59:00Z")); // 서울 23:59
        for (int i = 0; i < 3; i++) {
            submit(student(), UUID.randomUUID().toString(), TODAY, "B", photo());
        }
        assertProblem(
                submit(student(), UUID.randomUUID().toString(), TODAY, "B", photo()),
                HttpStatus.TOO_MANY_REQUESTS,
                "DAILY_LIMIT");

        clock.set(Instant.parse("2026-11-26T15:00:00Z")); // 서울 다음 날 00:00
        MvcTestResult next = submit(student(), UUID.randomUUID().toString(), TODAY, "B", photo());

        assertThat(next).hasStatus(HttpStatus.ACCEPTED);
        assertThat(jdbc.queryForObject(
                        "select max(attempt_no) from word_test_submission where student_id = ?",
                        Integer.class,
                        studentId))
                .isEqualTo(4);
    }

    @Test
    void 자정_직전_소수_초_제출은_내림해_저장해_다음_날_횟수에_들어가지_않는다() {
        // DATETIME은 소수 초를 반올림한다 — 23:59:59.6이 다음 날 00:00:00으로 저장되면 다음 날 한도를 하나 먹는다
        clock.set(Instant.parse("2026-11-26T14:59:59.600Z")); // 서울 23:59:59.6
        MvcTestResult late = submit(student(), UUID.randomUUID().toString(), TODAY, "B", photo());
        assertThat(late).hasStatus(HttpStatus.ACCEPTED);
        assertThat(jdbc.queryForObject(
                        "select created_at from word_test_submission where student_id = ?",
                        LocalDateTime.class,
                        studentId))
                .isEqualTo(LocalDateTime.of(2026, 11, 26, 14, 59, 59));

        clock.set(Instant.parse("2026-11-26T15:00:00Z")); // 서울 다음 날 00:00
        for (int i = 0; i < 3; i++) {
            assertThat(submit(student(), UUID.randomUUID().toString(), TODAY, "B", photo()))
                    .hasStatus(HttpStatus.ACCEPTED);
        }
    }

    @Test
    void 빈_사진은_400_INVALID_IMAGE() {
        String key = key();
        photos.put(key, new byte[0], "image/jpeg");

        assertProblem(
                submit(student(), UUID.randomUUID().toString(), TODAY, "B", key),
                HttpStatus.BAD_REQUEST,
                "INVALID_IMAGE");
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }

    private Cookie student() {
        return cookies.studentAccess(studentId, academyId);
    }

    private String key() {
        return key(studentId);
    }

    private String key(long student) {
        return "homework/" + academyId + "/" + student + "/" + UUID.randomUUID() + ".jpg";
    }

    /** 메모리 저장소에 올라간 내 사진 */
    private String photo() {
        return photo(studentId);
    }

    private String photo(long student) {
        String key = key(student);
        photos.put(key, new byte[] {1, 2, 3}, "image/jpeg");
        return key;
    }

    private MvcTestResult submit(Cookie access, String clientId, String lessonCode, String section, String imageKey) {
        return mvc.post()
                .uri(URL)
                .cookie(access, AuthCookieSupport.csrf())
                .header(AuthCookieSupport.CSRF_HEADER, AuthCookieSupport.CSRF_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientSubmissionId\":\"" + clientId + "\",\"lessonCode\":\"" + lessonCode
                        + "\",\"section\":\"" + section + "\",\"imageKey\":\"" + imageKey + "\"}")
                .exchange();
    }

    private static void assertProblem(MvcTestResult result, HttpStatus status, String code) {
        assertThat(result).hasStatus(status);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo(code);
    }
}
