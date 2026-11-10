package com.jglearning.jg4seasons.teacher.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.MutableClock;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.auth.AuthCookieSupport;
import com.jglearning.jg4seasons.auth.UserRole;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * 4.3 학생 상세(T-04). 오늘 = 서울 2026-11-10. 반 L2-B1(교사 담당), 진도표 1회차 어제·2회차 오늘·3회차 내일. 학생 범위는 4.2와 같다 — 그 반 현재 소속이면서
 * 그 학원 학생, 그 밖은 404. 회차 내용·단어는 지어낸 것.
 */
@IntegrationTest
@Transactional
class TeacherStudentDetailTest {

    static final Instant NOON_KST = Instant.parse("2026-11-10T03:00:00Z");
    static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

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
    long studentId;

    @BeforeEach
    void setUp() {
        clock.set(NOON_KST);
        data = new TestData(jdbc);
        academyId = data.academy("학생 상세 학원");
        teacherId = data.staff(academyId, "김교사", "teacher");
        classId = data.clazz(academyId, "L2-B1", "담당 반", TestData.uniqueAccessCode(), "active");
        data.assignTeacher(classId, teacherId);
        studentId = data.student(academyId, "김학생");
        data.enroll(classId, studentId);
        data.session(classId, 1, "L2-B1-01", TODAY.minusDays(1), "scheduled");
        data.session(classId, 2, "L2-B1-02", TODAY, "scheduled");
        data.session(classId, 3, "L2-B1-03", TODAY.plusDays(1), "scheduled");
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    private MvcTestResult detail(long actorId, UserRole role, long actorAcademy, long inClass, long student) {
        return mvc.get()
                .uri("/api/v1/teacher/classes/" + inClass + "/students/" + student)
                .cookie(cookies.access(actorId, role, actorAcademy))
                .exchange();
    }

    private MvcTestResult detail(long actorId, UserRole role, long inClass, long student) {
        return detail(actorId, role, academyId, inClass, student);
    }

    private MvcTestResult detail(long student) {
        return detail(teacherId, UserRole.TEACHER, classId, student);
    }

    @Test
    void 열린_회차만_seq_순으로_트랙_진도와_단어를_준다() {
        Map<String, Long> tracks = data.lessonContent("L2-B1-01", 2, 2, List.of("us_w", "us_m"));
        data.words("L2-B1-01", 4);
        jdbc.update(
                "insert into track_progress (student_id, track_id, merged_ranges, covered_ms, coverage, completed_at,"
                        + " total_play_ms, session_count, last_played_at) values (?, ?, '[]', 0, 0.9400, ?, 71000, 2, ?)",
                studentId,
                tracks.get("p1:us_w"),
                "2026-11-09 10:00:00",
                "2026-11-09 10:00:00");
        jdbc.update(
                "insert into lesson_progress (student_id, lesson_id, status, required_done, required_total)"
                        + " values (?, ?, 'in_progress', 1, 2)",
                studentId,
                data.lessonId("L2-B1-01"));
        jdbc.update(
                "insert into word_study (student_id, lesson_id, cards_viewed, mem_known, recall_best, spell_best,"
                        + " last_viewed_at) values (?, ?, 3, 2, 1, 0, ?)",
                studentId,
                data.lessonId("L2-B1-01"),
                "2026-11-09 10:00:00");
        int passageMs =
                jdbc.queryForObject("select duration_ms from track where id = ?", Integer.class, tracks.get("p1:us_w"));

        MvcTestResult result = detail(studentId);

        assertThat(result).hasStatusOk();
        var json = assertThat(result).bodyJson();
        json.extractingPath("$.lessons[*].code").isEqualTo(List.of("L2-B1-01", "L2-B1-02"));
        json.extractingPath("$.lessons[0].status").isEqualTo("in_progress");
        json.extractingPath("$.lessons[0].requiredDone").isEqualTo(1);
        json.extractingPath("$.lessons[0].requiredTotal").isEqualTo(2);
        json.extractingPath("$.lessons[0].tracks[*].variant").isEqualTo(List.of("us_w", "us_m", "us_w", "us_m"));
        json.extractingPath("$.lessons[0].tracks[*].passageNo").isEqualTo(List.of(1, 1, 2, 2));
        json.extractingPath("$.lessons[0].tracks[*].kind")
                .isEqualTo(List.of("passage", "passage", "passage", "passage"));
        json.extractingPath("$.lessons[0].tracks[*].required").isEqualTo(List.of(true, true, true, true));
        json.extractingPath("$.lessons[0].tracks[0].coverage").isEqualTo(0.94);
        json.extractingPath("$.lessons[0].tracks[0].completed").isEqualTo(true);
        json.extractingPath("$.lessons[0].tracks[0].totalPlayMs").isEqualTo(71000);
        json.extractingPath("$.lessons[0].tracks[0].durationMs").isEqualTo(passageMs);
        json.extractingPath("$.lessons[0].tracks[0].sessionCount").isEqualTo(2);
        json.extractingPath("$.lessons[0].tracks[0].lastPlayedAt").isEqualTo("2026-11-09T10:00:00Z");
        json.extractingPath("$.lessons[0].tracks[1].coverage").isEqualTo(0.0);
        json.extractingPath("$.lessons[0].tracks[1].completed").isEqualTo(false);
        json.extractingPath("$.lessons[0].tracks[1].totalPlayMs").isEqualTo(0);
        json.extractingPath("$.lessons[0].tracks[1].sessionCount").isEqualTo(0);
        json.extractingPath("$.lessons[0].tracks[1].lastPlayedAt").isNull();
        json.extractingPath("$.lessons[0].words")
                .isEqualTo(Map.of("total", 4, "cardsViewed", 3, "memKnown", 2, "recallBest", 1, "spellBest", 0));
        // 내용이 아직 없는 회차(지문 0)
        json.extractingPath("$.lessons[1].tracks").isEqualTo(List.of());
        json.extractingPath("$.lessons[1].status").isEqualTo("not_started");
        json.extractingPath("$.lessons[1].requiredDone").isEqualTo(0);
        json.extractingPath("$.lessons[1].requiredTotal").isEqualTo(0);
        json.extractingPath("$.lessons[1].words")
                .isEqualTo(Map.of("total", 0, "cardsViewed", 0, "memKnown", 0, "recallBest", 0, "spellBest", 0));
    }

    @Test
    void 저장된_지문_수가_지금_회차와_다르면_지금_지문_수로() {
        // 매트릭스(4.2)·학생 화면(2.3)과 같은 규칙
        data.lessonContent("L2-B1-01", 2, 1, List.of("us_w"));
        data.lessonContent("L2-B1-02", 4, 1, List.of("us_w"));
        jdbc.update(
                "insert into lesson_progress (student_id, lesson_id, status, required_done, required_total)"
                        + " values (?, ?, 'in_progress', 3, 6), (?, ?, 'in_progress', 1, 3)",
                studentId,
                data.lessonId("L2-B1-01"),
                studentId,
                data.lessonId("L2-B1-02"));

        var json = assertThat(detail(studentId)).bodyJson();
        json.extractingPath("$.lessons[0].requiredDone").isEqualTo(2);
        json.extractingPath("$.lessons[0].requiredTotal").isEqualTo(2);
        json.extractingPath("$.lessons[1].requiredDone").isEqualTo(1);
        json.extractingPath("$.lessons[1].requiredTotal").isEqualTo(4);
    }

    @Test
    void 학생_정보와_마지막_로그인() {
        long other = data.student(academyId, "박학생");
        data.enroll(classId, other);
        jdbc.update(
                "update `user` set school = 'A중', grade = '중1', last_login_at = ? where id = ?",
                "2026-11-09 01:02:03",
                studentId);

        var json = assertThat(detail(studentId)).bodyJson();
        json.extractingPath("$.student")
                .isEqualTo(Map.of(
                        "id", (int) studentId,
                        "name", "김학생",
                        "school", "A중",
                        "grade", "중1",
                        "lastLoginAt", "2026-11-09T01:02:03Z"));
        assertThat(detail(other))
                .bodyJson()
                .extractingPath("$.student.lastLoginAt")
                .isNull();
    }

    @Test
    void 휴강_행만_있는_회차는_빠진다() {
        jdbc.update("update class_session set status = 'cancelled' where class_id = ? and seq = 2", classId);

        assertThat(detail(studentId))
                .bodyJson()
                .extractingPath("$.lessons[*].code")
                .isEqualTo(List.of("L2-B1-01"));
    }

    @Test
    void 미래_회차_허용_반은_내일_회차도() {
        jdbc.update("update `class` set allow_future_lessons = true where id = ?", classId);

        assertThat(detail(studentId))
                .bodyJson()
                .extractingPath("$.lessons[*].code")
                .isEqualTo(List.of("L2-B1-01", "L2-B1-02", "L2-B1-03"));
    }

    @Test
    void 관리자는_담당이_아니어도_200() {
        long adminId = data.staff(academyId, "관리자", "admin");

        assertThat(detail(adminId, UserRole.ADMIN, classId, studentId)).hasStatusOk();
    }

    @Test
    void 비활성_교사는_401() {
        jdbc.update("update `user` set status = 'inactive' where id = ?", teacherId);

        assertThat(detail(studentId)).hasStatus(401);
    }

    @Test
    void 범위_밖이면_404() {
        long otherAcademy = data.academy("다른 학원");
        long otherTeacher = data.staff(otherAcademy, "다른 교사", "teacher");
        long notMine = data.staff(academyId, "옆 반 교사", "teacher");
        long otherClass = data.clazz(academyId, "L2-B1", "옆 반", TestData.uniqueAccessCode(), "active");
        long otherClassStudent = data.student(academyId, "옆반학생");
        data.enroll(otherClass, otherClassStudent);
        long left = data.student(academyId, "퇴원학생");
        data.enroll(classId, left);
        data.leave(classId, left);
        long foreign = data.student(otherAcademy, "남의학생");
        data.enroll(classId, foreign); // 소속 데이터가 어긋난 경우

        assertNotFound(detail(otherTeacher, UserRole.TEACHER, otherAcademy, classId, studentId), "다른 학원 교사");
        assertNotFound(detail(notMine, UserRole.TEACHER, classId, studentId), "담당 아닌 교사");
        assertNotFound(detail(otherClassStudent), "같은 학원 다른 반 학생");
        assertNotFound(detail(left), "퇴원한 학생");
        assertNotFound(detail(foreign), "반에 소속됐지만 다른 학원 학생");
        assertNotFound(detail(teacherId, UserRole.TEACHER, 999_999_999L, studentId), "없는 반");
    }

    @Test
    void 회차_내용_데이터_오류는_500_고정_문구() {
        Map<String, Long> tracks = data.lessonContent("L2-B1-01", 1, 2, List.of("us_w"));
        jdbc.update("update track set variant = 'p1:xx' where id = ?", tracks.get("p1:us_w"));

        MvcTestResult result = detail(studentId);

        assertThat(result).hasStatus(500);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INTERNAL");
        assertThat(result).bodyText().doesNotContain("variant").doesNotContain("p1:xx");
    }

    private static void assertNotFound(MvcTestResult result, String why) {
        assertThat(result).as(why).hasStatus(404);
        assertThat(result).as(why).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
    }
}
