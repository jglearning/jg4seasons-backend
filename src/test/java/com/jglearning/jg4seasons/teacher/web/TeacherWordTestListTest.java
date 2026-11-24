package com.jglearning.jg4seasons.teacher.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.MutableClock;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.auth.AuthCookieSupport;
import com.jglearning.jg4seasons.auth.UserRole;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
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
 * D-5 GET /teacher/classes/{classId}/word-tests?lessonCode=&section= — 반 학생(현재 소속, 이름 순)별 그 회차·영역 마지막 시도. 회차 열림·사용권은 보지 않는다(조회만).
 * 오늘 = 서울 2026-11-26, 1회차는 진도표상 아직 안 열림(미래 날짜)
 */
@IntegrationTest
@Transactional
class TeacherWordTestListTest {

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
    long lessonId;
    long kim;
    long park;
    long lee;

    @BeforeEach
    void setUp() {
        clock.set(NOON);
        data = new TestData(jdbc);
        academyId = data.academy("교사 단어시험 학원");
        enable(academyId, true);
        teacherId = data.staff(academyId, "김교사", "teacher");
        classId = data.clazz(academyId, "L2-B1", "담당 반", TestData.uniqueAccessCode(), "active");
        data.assignTeacher(classId, teacherId);
        park = data.student(academyId, "박학생");
        kim = data.student(academyId, "김학생");
        lee = data.student(academyId, "이학생");
        for (long s : List.of(park, kim, lee)) {
            data.enroll(classId, s);
        }
        // 아직 안 열린 회차(미래 날짜) — 교사 조회는 열림을 보지 않는다
        data.session(classId, 1, CODE, LocalDate.of(2026, 12, 3), "scheduled");
        lessonId = data.lessonId(CODE);
        for (int no = 1; no <= 4; no++) {
            jdbc.update(
                    "insert into word_test_item (lesson_id, section, no, prompt, answer) values (?, 'B', ?, ?, ?)",
                    lessonId,
                    no,
                    "뜻" + no,
                    "word" + no);
        }
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    @Test
    void 담당_교사는_반_학생을_이름_순으로_최근_제출과_함께_본다() {
        submission(kim, 1, "graded", 2);
        long kim2 = submission(kim, 2, "pending", null);
        long park1 = submission(park, 1, "graded", 3);

        MvcTestResult result = list(teacherId, UserRole.TEACHER, academyId, "lessonCode=" + CODE + "&section=B");

        assertThat(result).hasStatusOk();
        var json = assertThat(result).bodyJson();
        json.extractingPath("$.lessonCode").isEqualTo(CODE);
        json.extractingPath("$.students[*].name").isEqualTo(List.of("김학생", "박학생", "이학생"));
        json.extractingPath("$.students[*].studentId").isEqualTo(List.of((int) kim, (int) park, (int) lee));
        json.extractingPath("$.students[0].submission.id").isEqualTo((int) kim2);
        json.extractingPath("$.students[0].submission.attemptNo").isEqualTo(2);
        json.extractingPath("$.students[0].submission.status").isEqualTo("pending");
        json.hasPathSatisfying(
                "$.students[0].submission.score", v -> assertThat(v).isNull());
        json.extractingPath("$.students[1].submission.id").isEqualTo((int) park1);
        json.extractingPath("$.students[1].submission.score").isEqualTo(3);
        json.extractingPath("$.students[1].submission.total").isEqualTo(4);
        json.extractingPath("$.students[1].submission.submittedAt").isEqualTo("2026-11-26T03:00:00Z");
        json.hasPathSatisfying("$.students[2].submission", v -> assertThat(v).isNull());
    }

    @Test
    void 관리자도_본다() {
        long adminId = data.staff(academyId, "관리자", "admin");

        assertThat(list(adminId, UserRole.ADMIN, academyId, "lessonCode=" + CODE + "&section=B"))
                .hasStatusOk();
    }

    @Test
    void 끝난_반은_교사에게_404이고_관리자는_본다() {
        // D-6(제출 상세)의 교사 범위가 진행 중 반만이라 목록도 같게 — 목록에서 누른 행이 404가 되지 않게
        jdbc.update("update `class` set status = 'finished' where id = ?", classId);
        long adminId = data.staff(academyId, "관리자", "admin");

        assertNotFound(list(teacherId, UserRole.TEACHER, academyId, "lessonCode=" + CODE + "&section=B"), "NOT_FOUND");
        assertThat(list(adminId, UserRole.ADMIN, academyId, "lessonCode=" + CODE + "&section=B"))
                .hasStatusOk();
    }

    @Test
    void 반을_떠난_학생은_목록에_없다() {
        submission(lee, 1, "graded", 4);
        data.leave(classId, lee);

        assertThat(list(teacherId, UserRole.TEACHER, academyId, "lessonCode=" + CODE + "&section=B"))
                .bodyJson()
                .extractingPath("$.students[*].name")
                .isEqualTo(List.of("김학생", "박학생"));
    }

    @Test
    void 담당이_아닌_반과_다른_학원_교사는_404() {
        long notMine = data.staff(academyId, "옆 반 교사", "teacher");
        long otherAcademy = data.academy("다른 학원");
        enable(otherAcademy, true);
        long otherTeacher = data.staff(otherAcademy, "다른 교사", "teacher");

        assertNotFound(list(notMine, UserRole.TEACHER, academyId, "lessonCode=" + CODE + "&section=B"), "NOT_FOUND");
        assertNotFound(
                list(otherTeacher, UserRole.TEACHER, otherAcademy, "lessonCode=" + CODE + "&section=B"), "NOT_FOUND");
    }

    @Test
    void 기능이_꺼진_학원은_404_FEATURE_DISABLED() {
        enable(academyId, false);

        assertNotFound(
                list(teacherId, UserRole.TEACHER, academyId, "lessonCode=" + CODE + "&section=B"), "FEATURE_DISABLED");
    }

    @Test
    void 진도표에_없는_회차와_시험지_없는_영역은_404() {
        // 같은 권 2회차는 시험지가 있어도 이 반 진도표에 없으면 404(시험지 검사보다 진도표 검사가 먼저 막는지)
        jdbc.update(
                "insert into word_test_item (lesson_id, section, no, prompt, answer) values (?, 'B', 1, '뜻', 'word')",
                data.lessonId("L2-B1-02"));
        assertNotFound(list(teacherId, UserRole.TEACHER, academyId, "lessonCode=L2-B1-02&section=B"), "NOT_FOUND");
        assertNotFound(list(teacherId, UserRole.TEACHER, academyId, "lessonCode=ZZ-9&section=B"), "NOT_FOUND");
        assertNotFound(list(teacherId, UserRole.TEACHER, academyId, "lessonCode=" + CODE + "&section=C"), "NOT_FOUND");
    }

    @Test
    void 영역과_회차_코드_형식이_틀리면_400() {
        for (String query : Arrays.asList(
                "lessonCode=" + CODE + "&section=E",
                "lessonCode=" + CODE + "&section=b",
                "lessonCode=" + CODE,
                "lessonCode=bad%20code&section=B",
                "section=B")) {
            MvcTestResult result = list(teacherId, UserRole.TEACHER, academyId, query);
            assertThat(result).as(query).hasStatus(400);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION");
        }
    }

    @Test
    void 학생은_403() {
        MvcTestResult result = mvc.get()
                .uri("/api/v1/teacher/classes/" + classId + "/word-tests?lessonCode=" + CODE + "&section=B")
                .cookie(cookies.studentAccess(kim, academyId))
                .exchange();

        assertThat(result).hasStatus(403);
    }

    private MvcTestResult list(long userId, UserRole role, long academy, String query) {
        Cookie access = cookies.access(userId, role, academy);
        return mvc.get()
                .uri("/api/v1/teacher/classes/" + classId + "/word-tests?" + query)
                .cookie(access)
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

    private long submission(long student, int attempt, String status, Integer score) {
        jdbc.update(
                "insert into word_test_submission (client_submission_id, student_id, lesson_id, section, attempt_no,"
                        + " image_key, status, score, total, created_at) values (?, ?, ?, 'B', ?, 'homework/k.jpg', ?, ?, 4, ?)",
                UUID.randomUUID().toString(),
                student,
                lessonId,
                attempt,
                status,
                score,
                LocalDateTime.of(2026, 11, 26, 3, 0));
        return jdbc.queryForObject("select last_insert_id()", Long.class);
    }
}
