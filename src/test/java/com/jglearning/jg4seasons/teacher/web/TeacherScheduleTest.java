package com.jglearning.jg4seasons.teacher.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.MutableClock;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.auth.AuthCookieSupport;
import com.jglearning.jg4seasons.auth.UserRole;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * 4.5~4.7 교사 진도표(T-06, ADR 0030). 오늘 = 서울 2026-11-10(화). 반 L2-B1(교사 담당, 수업 요일 화·목). 진도표: 1 L2-B1-01 11-05(목) · 2 L2-B1-02
 * 11-10(오늘) · 3 L2-B1-03 11-12(목).
 */
@IntegrationTest
@Transactional
class TeacherScheduleTest {

    static final Instant NOON_KST = Instant.parse("2026-11-10T03:00:00Z");

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

    static LocalDate d(int day) {
        return LocalDate.of(2026, 11, day);
    }

    @BeforeEach
    void setUp() {
        clock.set(NOON_KST);
        data = new TestData(jdbc);
        academyId = data.academy("진도표 학원");
        teacherId = data.staff(academyId, "김교사", "teacher");
        classId = data.clazz(academyId, "L2-B1", "담당 반", TestData.uniqueAccessCode(), "active");
        data.assignTeacher(classId, teacherId);
        data.session(classId, 1, "L2-B1-01", d(5), "scheduled");
        data.session(classId, 2, "L2-B1-02", d(10), "scheduled");
        data.session(classId, 3, "L2-B1-03", d(12), "scheduled");
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    private MvcTestResult sessions(long actorId, UserRole role, long actorAcademy, long inClass) {
        return mvc.get()
                .uri("/api/v1/teacher/classes/" + inClass + "/sessions")
                .cookie(cookies.access(actorId, role, actorAcademy))
                .exchange();
    }

    private MvcTestResult sessions() {
        return sessions(teacherId, UserRole.TEACHER, academyId, classId);
    }

    private MvcTestResult write(
            HttpMethod method, long actorId, UserRole role, long actorAcademy, long inClass, String path, String body) {
        return mvc.method(method)
                .uri("/api/v1/teacher/classes/" + inClass + "/sessions/" + path)
                .cookie(cookies.access(actorId, role, actorAcademy), AuthCookieSupport.csrf())
                .header(AuthCookieSupport.CSRF_HEADER, AuthCookieSupport.CSRF_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private MvcTestResult cancel(int seq, String body) {
        return write(HttpMethod.POST, teacherId, UserRole.TEACHER, academyId, classId, seq + "/cancel", body);
    }

    private MvcTestResult update(int seq, String body) {
        return write(HttpMethod.PATCH, teacherId, UserRole.TEACHER, academyId, classId, String.valueOf(seq), body);
    }

    private MvcTestResult studentLesson(long studentId, String code) {
        return mvc.get()
                .uri("/api/v1/student/lessons/" + code)
                .cookie(cookies.studentAccess(studentId, academyId))
                .exchange();
    }

    /** DB의 진도표(seq 순): "seq 코드 날짜 상태 메모" */
    private List<String> rows() {
        return jdbc.query(
                "select s.seq, l.code, s.scheduled_date, s.status, s.note from class_session s"
                        + " join lesson l on l.id = s.lesson_id where s.class_id = ? order by s.seq",
                (rs, i) -> rs.getInt(1) + " " + rs.getString(2) + " " + rs.getString(3) + " " + rs.getString(4) + " "
                        + rs.getString(5),
                classId);
    }

    private static void assertValidation(MvcTestResult result, String field) {
        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION");
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo(field);
    }

    private static Map<String, Object> item(int seq, String code, String date, String status, String note) {
        Map<String, Object> lesson =
                Map.of("code", code, "title", "Reading Day " + Integer.parseInt(code.substring(6)));
        Map<String, Object> item = new HashMap<>();
        item.put("seq", seq);
        item.put("lesson", lesson);
        item.put("scheduledDate", date);
        item.put("status", status);
        item.put("note", note);
        return item;
    }

    @Test
    void 진도표는_휴강_행을_포함해_seq_순() {
        jdbc.update(
                "update class_session set status = 'cancelled', note = '내신 휴강' where class_id = ? and seq = 2",
                classId);
        data.session(classId, 4, "L2-B1-02", d(17), "scheduled");

        MvcTestResult result = sessions();

        assertThat(result).hasStatusOk();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.items")
                .isEqualTo(List.of(
                        item(1, "L2-B1-01", "2026-11-05", "done", null),
                        item(2, "L2-B1-02", "2026-11-10", "cancelled", "내신 휴강"),
                        item(3, "L2-B1-03", "2026-11-12", "scheduled", null),
                        item(4, "L2-B1-02", "2026-11-17", "scheduled", null)));
    }

    // 응답 상태: done = 학원 날짜로 오늘 이전(응답 때 계산, DB에 쓰지 않음 — 이슈 #45)

    @Test
    void 지난_수업은_done_오늘_수업은_scheduled() {
        assertThat(sessions())
                .bodyJson()
                .extractingPath("$.items[*].status")
                .isEqualTo(List.of("done", "scheduled", "scheduled"));
        assertThat(rows())
                .as("DB는 그대로")
                .containsExactly(
                        "1 L2-B1-01 2026-11-05 scheduled null",
                        "2 L2-B1-02 2026-11-10 scheduled null",
                        "3 L2-B1-03 2026-11-12 scheduled null");
    }

    @Test
    void 지난_휴강은_cancelled() {
        jdbc.update("update class_session set status = 'cancelled' where class_id = ? and seq = 1", classId);

        assertThat(sessions())
                .bodyJson()
                .extractingPath("$.items[*].status")
                .isEqualTo(List.of("cancelled", "scheduled", "scheduled"));
    }

    @Test
    void done_판정은_학원_날짜_자정이_기준() {
        // KST 11-11 00:10 = UTC 11-10 15:10. 학원 날짜로는 11-10 행이 어제 — UTC 날짜로 판정하면 오늘이라 scheduled가 된다
        clock.set(Instant.parse("2026-11-10T15:10:00Z"));

        assertThat(sessions())
                .bodyJson()
                .extractingPath("$.items[*].status")
                .isEqualTo(List.of("done", "done", "scheduled"));
    }

    @Test
    void 편집_응답의_done은_DB에_쓰지_않는다() {
        MvcTestResult result = cancel(3, "{\"shiftFollowing\":true}");

        assertThat(result).bodyJson().extractingPath("$.items[0].status").isEqualTo("done");
        assertThat(rows()).first().isEqualTo("1 L2-B1-01 2026-11-05 scheduled null");
    }

    // 4.7 휴강

    @Test
    void 오늘_수업을_휴강하고_밀면_학생은_다음_수업일에_연다() {
        long studentId = data.student(academyId, "김학생");
        data.enroll(classId, studentId);
        assertThat(studentLesson(studentId, "L2-B1-02")).as("휴강 전 오늘 회차").hasStatusOk();

        MvcTestResult result = cancel(2, "{\"note\":\"체험학습\",\"shiftFollowing\":true}");

        assertThat(result).hasStatusOk();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.items")
                .isEqualTo(List.of(
                        item(1, "L2-B1-01", "2026-11-05", "done", null),
                        item(2, "L2-B1-02", "2026-11-10", "cancelled", "체험학습"),
                        item(3, "L2-B1-02", "2026-11-12", "scheduled", null),
                        item(4, "L2-B1-03", "2026-11-17", "scheduled", null)));
        assertThat(rows())
                .as("응답 = DB")
                .containsExactly(
                        "1 L2-B1-01 2026-11-05 scheduled null",
                        "2 L2-B1-02 2026-11-10 cancelled 체험학습",
                        "3 L2-B1-02 2026-11-12 scheduled null",
                        "4 L2-B1-03 2026-11-17 scheduled null");

        MvcTestResult today = studentLesson(studentId, "L2-B1-02");
        assertThat(today).as("오늘은 잠김").hasStatus(423);
        assertThat(today).bodyJson().extractingPath("$.code").isEqualTo("LESSON_LOCKED");
        clock.set(Instant.parse("2026-11-12T03:00:00Z"));
        assertThat(studentLesson(studentId, "L2-B1-02")).as("다음 수업일에 열림").hasStatusOk();
    }

    @Test
    void 밀지_않는_휴강은_그_회차를_건너뛴다() {
        long studentId = data.student(academyId, "김학생");
        data.enroll(classId, studentId);

        assertThat(cancel(2, "{\"shiftFollowing\":false}")).hasStatusOk();

        assertThat(rows())
                .containsExactly(
                        "1 L2-B1-01 2026-11-05 scheduled null",
                        "2 L2-B1-02 2026-11-10 cancelled null",
                        "3 L2-B1-03 2026-11-12 scheduled null");
        clock.set(Instant.parse("2026-11-20T03:00:00Z"));
        assertThat(studentLesson(studentId, "L2-B1-02")).as("휴강 행만 남은 회차").hasStatus(423);
    }

    @Test
    void 이미_휴강한_행은_그대로_200() {
        assertThat(cancel(2, "{\"note\":\"체험학습\",\"shiftFollowing\":true}")).hasStatusOk();
        List<String> before = rows();
        assertThat(before).as("첫 휴강 반영").hasSize(4).contains("2 L2-B1-02 2026-11-10 cancelled 체험학습");

        assertThat(cancel(2, "{\"note\":\"다른 메모\",\"shiftFollowing\":true}")).hasStatusOk();

        assertThat(rows()).isEqualTo(before);
    }

    @Test
    void 지난_수업은_휴강도_날짜_변경도_409_메모는_된다() {
        MvcTestResult cancelPast = cancel(1, "{\"shiftFollowing\":true}");
        assertThat(cancelPast).hasStatus(409);
        assertThat(cancelPast).bodyJson().extractingPath("$.code").isEqualTo("SESSION_PAST");
        assertThat(update(1, "{\"scheduledDate\":\"2026-11-11\"}")).hasStatus(409);

        assertThat(update(1, "{\"note\":\"복습 필요\"}")).hasStatusOk();
        assertThat(rows()).first().isEqualTo("1 L2-B1-01 2026-11-05 scheduled 복습 필요");
    }

    @Test
    void 수정_폼이_원래_날짜와_메모를_함께_보내도_지난_행_메모가_바뀐다() {
        assertThat(update(1, "{\"scheduledDate\":\"2026-11-05\",\"note\":\"결석 많음\"}"))
                .hasStatusOk();

        assertThat(rows()).first().isEqualTo("1 L2-B1-01 2026-11-05 scheduled 결석 많음");
    }

    @Test
    void 지난_수업_판정은_학원_날짜_자정이_기준() {
        // KST 11-11 00:10 = UTC 11-10 15:10. 학원 날짜로는 11-10 행이 어제 — UTC 날짜로 판정하면 오늘이라 통과해 버린다
        clock.set(Instant.parse("2026-11-10T15:10:00Z"));

        MvcTestResult result = cancel(2, "{\"shiftFollowing\":true}");

        assertThat(result).hasStatus(409);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("SESSION_PAST");
    }

    // 4.6 날짜·메모 변경

    @Test
    void 날짜를_바꾸면_응답과_DB에_반영된다() {
        assertThat(update(3, "{\"scheduledDate\":\"2026-11-13\"}"))
                .bodyJson()
                .extractingPath("$.items[2].scheduledDate")
                .isEqualTo("2026-11-13");

        assertThat(update(2, "{\"scheduledDate\":\"2026-11-11\",\"shiftFollowing\":true}"))
                .hasStatusOk();
        assertThat(rows())
                .as("뒤 수업은 새 날짜의 다음 수업일(목)부터")
                .containsExactly(
                        "1 L2-B1-01 2026-11-05 scheduled null",
                        "2 L2-B1-02 2026-11-11 scheduled null",
                        "3 L2-B1-03 2026-11-12 scheduled null");
    }

    @Test
    void 날짜_순서를_어기면_400() {
        assertValidation(update(3, "{\"scheduledDate\":\"2026-11-10\"}"), "scheduledDate");
        assertValidation(update(2, "{\"scheduledDate\":\"2026-11-12\"}"), "scheduledDate");
        assertValidation(update(2, "{\"scheduledDate\":\"2026-11-09\"}"), "scheduledDate");
        // @Transactional 테스트라 예외 전에 쓴 내용도 보인다 — 행 수가 아니라 내용 전체로 "바뀐 것 없음"을 본다
        assertThat(rows())
                .as("바뀐 것 없음")
                .isEqualTo(List.of(
                        "1 L2-B1-01 2026-11-05 scheduled null",
                        "2 L2-B1-02 2026-11-10 scheduled null",
                        "3 L2-B1-03 2026-11-12 scheduled null"));
    }

    @Test
    void 쓰기도_범위_밖이면_404() {
        long otherAcademy = data.academy("다른 학원");
        long otherTeacher = data.staff(otherAcademy, "다른 교사", "teacher");
        long notMine = data.staff(academyId, "옆 반 교사", "teacher");
        String body = "{\"shiftFollowing\":true}";

        assertNotFound(
                write(HttpMethod.POST, otherTeacher, UserRole.TEACHER, otherAcademy, classId, "2/cancel", body),
                "다른 학원 교사");
        assertNotFound(
                write(HttpMethod.POST, notMine, UserRole.TEACHER, academyId, classId, "2/cancel", body), "담당 아닌 교사");
        assertNotFound(cancel(99, body), "없는 행");
        assertNotFound(update(99, "{\"note\":\"a\"}"), "없는 행");
        // @Transactional 테스트라 예외 전에 쓴 내용도 보인다 — 행 수가 아니라 내용 전체로 "바뀐 것 없음"을 본다
        assertThat(rows())
                .as("바뀐 것 없음")
                .isEqualTo(List.of(
                        "1 L2-B1-01 2026-11-05 scheduled null",
                        "2 L2-B1-02 2026-11-10 scheduled null",
                        "3 L2-B1-03 2026-11-12 scheduled null"));
    }

    @Test
    void 관리자는_담당_아닌_반도_휴강한다() {
        long adminId = data.staff(academyId, "관리자", "admin");

        assertThat(write(
                        HttpMethod.POST,
                        adminId,
                        UserRole.ADMIN,
                        academyId,
                        classId,
                        "3/cancel",
                        "{\"shiftFollowing\":true}"))
                .hasStatusOk();
    }

    @Test
    void 관리자는_담당이_아니어도_200() {
        long adminId = data.staff(academyId, "관리자", "admin");

        assertThat(sessions(adminId, UserRole.ADMIN, academyId, classId)).hasStatusOk();
    }

    @Test
    void 비활성_교사는_401() {
        jdbc.update("update `user` set status = 'inactive' where id = ?", teacherId);

        assertThat(sessions()).hasStatus(401);
    }

    @Test
    void 범위_밖이면_404() {
        long otherAcademy = data.academy("다른 학원");
        long otherTeacher = data.staff(otherAcademy, "다른 교사", "teacher");
        long notMine = data.staff(academyId, "옆 반 교사", "teacher");

        assertNotFound(sessions(otherTeacher, UserRole.TEACHER, otherAcademy, classId), "다른 학원 교사");
        assertNotFound(sessions(notMine, UserRole.TEACHER, academyId, classId), "담당 아닌 교사");
        assertNotFound(sessions(teacherId, UserRole.TEACHER, academyId, 999_999_999L), "없는 반");
    }

    static void assertNotFound(MvcTestResult result, String why) {
        assertThat(result).as(why).hasStatus(404);
        assertThat(result).as(why).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
    }
}
