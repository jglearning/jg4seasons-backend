package com.jglearning.jg4seasons.teacher.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.MutableClock;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.auth.AuthCookieSupport;
import com.jglearning.jg4seasons.auth.UserRole;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * D-6 GET /teacher/word-tests/{id} — 교사는 진행 중 담당 반에 지금 소속된 학생의 제출만, 관리자는 학원 학생 전체. 사진은 짧은 서명 주소(테스트 저장소는
 * https://photos.test/키?view), unclear 문항에도 정답
 */
@IntegrationTest
@Transactional
class TeacherWordTestDetailTest {

    static final String CODE = "L2-B1-01";
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
    long teacherId;
    long classId;
    long otherClassId;
    long lessonId;
    long kim;
    long park;
    long[] items = new long[5];

    @BeforeEach
    void setUp() {
        clock.set(NOON);
        data = new TestData(jdbc);
        academyId = data.academy("교사 상세 단어시험 학원");
        enable(academyId, true);
        teacherId = data.staff(academyId, "김교사", "teacher");
        classId = data.clazz(academyId, "L2-B1", "담당 반", TestData.uniqueAccessCode(), "active");
        otherClassId = data.clazz(academyId, "L2-B1", "옆 반", TestData.uniqueAccessCode(), "active");
        data.assignTeacher(classId, teacherId);
        kim = data.student(academyId, "김학생");
        park = data.student(academyId, "박학생");
        data.enroll(classId, kim);
        data.enroll(otherClassId, park);
        data.session(classId, 1, CODE, LocalDate.of(2026, 11, 26), "scheduled");
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

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    @Test
    void 담당_반_학생의_graded_상세는_사진_주소와_문항별_결과이고_unclear에도_정답() {
        long id = graded(kim);

        MvcTestResult result = detail(teacherId, UserRole.TEACHER, academyId, id);

        assertThat(result).hasStatusOk();
        var json = assertThat(result).bodyJson();
        json.extractingPath("$.id").isEqualTo((int) id);
        json.extractingPath("$.lessonCode").isEqualTo(CODE);
        json.extractingPath("$.section").isEqualTo("B");
        json.extractingPath("$.attemptNo").isEqualTo(1);
        json.extractingPath("$.status").isEqualTo("graded");
        json.extractingPath("$.score").isEqualTo(1);
        json.extractingPath("$.total").isEqualTo(4);
        json.extractingPath("$.submittedAt").isEqualTo("2026-11-26T03:00:00Z");
        json.extractingPath("$.gradedAt").isEqualTo("2026-11-26T03:00:09Z");
        json.extractingPath("$.student.id").isEqualTo((int) kim);
        json.extractingPath("$.student.name").isEqualTo("김학생");
        json.extractingPath("$.imageUrl").isEqualTo("https://photos.test/homework/k.jpg?view");
        json.extractingPath("$.items[*].no").isEqualTo(List.of(1, 2, 3, 4));
        json.extractingPath("$.items[*].result").isEqualTo(List.of("wrong", "correct", "blank", "unclear"));
        json.extractingPath("$.items[*].written").isEqualTo(List.of("beautful", "library", "", ""));
        json.extractingPath("$.items[*].answer").isEqualTo(List.of("beautiful", "library", "receive", "atmosphere"));
    }

    @Test
    void graded_전에는_items_칸이_없고_사진_키가_없으면_imageUrl_null() {
        long pending = submission(kim, 1, "pending", null, "homework/k.jpg");
        long noPhoto = submission(kim, 2, "failed", null, null);

        var pendingJson = assertThat(detail(teacherId, UserRole.TEACHER, academyId, pending))
                .bodyJson();
        pendingJson.doesNotHavePath("$.items");
        pendingJson.hasPathSatisfying("$.score", v -> assertThat(v).isNull());
        pendingJson.hasPathSatisfying("$.gradedAt", v -> assertThat(v).isNull());
        assertThat(detail(teacherId, UserRole.TEACHER, academyId, noPhoto))
                .bodyJson()
                .hasPathSatisfying("$.imageUrl", v -> assertThat(v).isNull());
    }

    @Test
    void 관리자는_학원_어느_학생의_제출이든_본다() {
        long adminId = data.staff(academyId, "관리자", "admin");
        long id = graded(park);

        assertThat(detail(adminId, UserRole.ADMIN, academyId, id)).hasStatusOk();
    }

    @Test
    void 담당이_아닌_반_학생의_제출은_404() {
        assertNotFound(detail(teacherId, UserRole.TEACHER, academyId, graded(park)), "NOT_FOUND");
    }

    @Test
    void 반을_옮긴_학생의_제출은_옛_담당_교사에게_404() {
        long id = graded(kim);
        data.leave(classId, kim);
        data.enroll(otherClassId, kim);

        assertNotFound(detail(teacherId, UserRole.TEACHER, academyId, id), "NOT_FOUND");
    }

    @Test
    void 끝난_반_학생의_제출은_교사에게_404이고_관리자는_본다() {
        long id = graded(kim);
        jdbc.update("update `class` set status = 'finished' where id = ?", classId);
        long adminId = data.staff(academyId, "관리자", "admin");

        assertNotFound(detail(teacherId, UserRole.TEACHER, academyId, id), "NOT_FOUND");
        assertThat(detail(adminId, UserRole.ADMIN, academyId, id)).hasStatusOk();
    }

    @Test
    void 다른_학원_교사와_관리자는_404() {
        long id = graded(kim);
        long otherAcademy = data.academy("다른 학원");
        enable(otherAcademy, true);
        long otherTeacher = data.staff(otherAcademy, "다른 교사", "teacher");
        long otherAdmin = data.staff(otherAcademy, "다른 관리자", "admin");

        assertNotFound(detail(otherTeacher, UserRole.TEACHER, otherAcademy, id), "NOT_FOUND");
        assertNotFound(detail(otherAdmin, UserRole.ADMIN, otherAcademy, id), "NOT_FOUND");
    }

    @Test
    void 없는_제출은_404() {
        assertNotFound(detail(teacherId, UserRole.TEACHER, academyId, 999_999_999L), "NOT_FOUND");
    }

    @Test
    void 기능이_꺼진_학원은_404_FEATURE_DISABLED() {
        // 첫 요청 전에 끈다 — 테스트 트랜잭션 안에서는 학원 엔티티가 첫 요청 때 1차 캐시에 남는다
        long id = graded(kim);
        enable(academyId, false);

        assertNotFound(detail(teacherId, UserRole.TEACHER, academyId, id), "FEATURE_DISABLED");
    }

    @Test
    void 학생은_403() {
        long id = graded(kim);

        assertThat(mvc.get()
                        .uri("/api/v1/teacher/word-tests/" + id)
                        .cookie(cookies.studentAccess(kim, academyId))
                        .exchange())
                .hasStatus(403);
    }

    private long graded(long student) {
        long id = submission(student, 1, "graded", 1, "homework/k.jpg");
        jdbc.update("update word_test_submission set graded_at = '2026-11-26 03:00:09' where id = ?", id);
        answer(id, items[1], "beautful", null, "wrong");
        answer(id, items[2], "library", null, "correct");
        answer(id, items[3], null, null, "blank");
        answer(id, items[4], null, "unreadable", "unclear");
        return id;
    }

    private MvcTestResult detail(long userId, UserRole role, long academy, long id) {
        return mvc.get()
                .uri("/api/v1/teacher/word-tests/" + id)
                .cookie(cookies.access(userId, role, academy))
                .exchange();
    }

    private static void assertNotFound(MvcTestResult result, String code) {
        assertThat(result).hasStatus(404);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo(code);
    }

    private void enable(long academy, boolean on) {
        jdbc.update(
                "update academy set settings = ? where id = ?",
                on ? "{\"timezone\":\"Asia/Seoul\",\"features\":{\"wordTest\":true}}" : "{\"timezone\":\"Asia/Seoul\"}",
                academy);
    }

    private long submission(long student, int attempt, String status, Integer score, String key) {
        jdbc.update(
                "insert into word_test_submission (client_submission_id, student_id, lesson_id, section, attempt_no,"
                        + " image_key, status, score, total, created_at) values (?, ?, ?, 'B', ?, ?, ?, ?, 4, ?)",
                UUID.randomUUID().toString(),
                student,
                lessonId,
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
