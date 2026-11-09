package com.jglearning.jg4seasons.teacher.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.MutableClock;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.auth.AuthCookieSupport;
import com.jglearning.jg4seasons.auth.UserRole;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

/** 4.2 청취 현황 매트릭스(T-03). 학원 타임존 기본 Asia/Seoul, 오늘 = 2026-11-10. */
@IntegrationTest
@Transactional
class TeacherMatrixTest {

    /** 2026-11-10 12:00 KST */
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

    @BeforeEach
    void setUp() {
        clock.set(NOON_KST);
        data = new TestData(jdbc);
        academyId = data.academy("매트릭스 학원");
        teacherId = data.staff(academyId, "김교사", "teacher");
        classId = data.clazz(academyId, "L2-B1", "담당 반", TestData.uniqueAccessCode(), "active");
        data.assignTeacher(classId, teacherId);
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    private MvcTestResult matrix(long actorId, UserRole role, long inClass, String query) {
        return mvc.get()
                .uri("/api/v1/teacher/classes/" + inClass + "/matrix" + query)
                .cookie(cookies.access(actorId, role, academyId))
                .exchange();
    }

    private MvcTestResult matrix(String query) {
        return matrix(teacherId, UserRole.TEACHER, classId, query);
    }

    private MvcTestResult matrix() {
        return matrix("");
    }

    private static String code(int number) {
        return String.format("L2-B1-%02d", number);
    }

    /** seq i = 회차 i, 수업일은 오늘 회차(todaySeq)를 기준으로 하루씩 */
    private void sessions(int count, int todaySeq) {
        for (int i = 1; i <= count; i++) {
            data.session(classId, i, code(i), TODAY.plusDays(i - todaySeq), "scheduled");
        }
    }

    private long enrolled(String name) {
        long id = data.student(academyId, name);
        data.enroll(classId, id);
        return id;
    }

    private void progress(long student, String lessonCode, String status, int done, int total, String completedAtUtc) {
        jdbc.update(
                "insert into lesson_progress (student_id, lesson_id, status, required_done, required_total,"
                        + " completed_at) values (?, ?, ?, ?, ?, ?)",
                student,
                data.lessonId(lessonCode),
                status,
                done,
                total,
                completedAtUtc == null ? null : LocalDateTime.parse(completedAtUtc));
    }

    private static <T> T read(MvcTestResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }

    private static String cell(long studentId, String lessonCode) {
        return "$.cells['" + studentId + "']['" + lessonCode + "']";
    }

    @Test
    void 기본_범위는_오늘_회차가_든_8개_묶음() throws Exception {
        sessions(12, 10);

        MvcTestResult result = matrix();

        assertThat(result).hasStatusOk();
        assertThat((List<Integer>) read(result, "$.sessions[*].seq")).containsExactly(9, 10, 11, 12);
        assertThat((List<Boolean>) read(result, "$.sessions[*].isCurrent")).containsExactly(false, true, false, false);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.sessions[1].lesson.code")
                .isEqualTo("L2-B1-10");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.sessions[1].lesson.number")
                .isEqualTo(10);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.sessions[1].scheduledDate")
                .isEqualTo("2026-11-10");
    }

    @Test
    void 개강_전이면_1부터_8() throws Exception {
        sessions(10, 0); // 모두 내일 이후

        assertThat((List<Integer>) read(matrix(), "$.sessions[*].seq")).containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
    }

    @Test
    void 보낸_범위를_그대로_쓴다() throws Exception {
        sessions(12, 10);

        assertThat((List<Integer>) read(matrix("?fromSeq=3&toSeq=5"), "$.sessions[*].seq"))
                .containsExactly(3, 4, 5);
    }

    @Test
    void 열린_회차는_학생마다_칸이_있고_열리지_않은_회차는_칸이_없다() throws Exception {
        sessions(3, 2);
        data.lessonContent(code(1), 3, 1, List.of("1.0"));
        long a = enrolled("가");

        MvcTestResult result = matrix();

        assertThat((List<Boolean>) read(result, "$.sessions[*].opened")).containsExactly(true, true, false);
        Map<String, Object> cells = read(result, "$.cells['" + a + "']");
        assertThat(cells).containsOnlyKeys(code(1), code(2));
        assertThat(result)
                .bodyJson()
                .extractingPath(cell(a, code(1)) + ".status")
                .isEqualTo("not_started");
        assertThat(result)
                .bodyJson()
                .extractingPath(cell(a, code(1)) + ".requiredDone")
                .isEqualTo(0);
        assertThat(result)
                .bodyJson()
                .extractingPath(cell(a, code(1)) + ".requiredTotal")
                .isEqualTo(3);
        assertThat(result)
                .bodyJson()
                .extractingPath(cell(a, code(1)) + ".completedAt")
                .isNull();
    }

    @Test
    void 칸은_진도_그대로() {
        sessions(3, 2);
        long a = enrolled("가");
        progress(a, code(1), "completed", 6, 6, "2026-11-09T10:15:30");
        progress(a, code(2), "in_progress", 3, 6, null);

        MvcTestResult result = matrix();

        assertThat(result)
                .bodyJson()
                .extractingPath(cell(a, code(1)) + ".status")
                .isEqualTo("completed");
        assertThat(result)
                .bodyJson()
                .extractingPath(cell(a, code(1)) + ".requiredDone")
                .isEqualTo(6);
        assertThat(result)
                .bodyJson()
                .extractingPath(cell(a, code(1)) + ".requiredTotal")
                .isEqualTo(6);
        assertThat(result)
                .bodyJson()
                .extractingPath(cell(a, code(1)) + ".completedAt")
                .isEqualTo("2026-11-09T10:15:30Z");
        assertThat(result)
                .bodyJson()
                .extractingPath(cell(a, code(2)) + ".status")
                .isEqualTo("in_progress");
        assertThat(result)
                .bodyJson()
                .extractingPath(cell(a, code(2)) + ".requiredDone")
                .isEqualTo(3);
    }

    @Test
    void 지난_회차_미완료는_overdue_오늘_회차는_아니다() {
        sessions(3, 3);
        long a = enrolled("가");
        progress(a, code(1), "completed", 6, 6, "2026-11-08T01:00:00");
        progress(a, code(2), "in_progress", 1, 6, null);

        MvcTestResult result = matrix();

        assertThat(result)
                .bodyJson()
                .extractingPath(cell(a, code(1)) + ".overdue")
                .isEqualTo(false);
        assertThat(result)
                .bodyJson()
                .extractingPath(cell(a, code(2)) + ".overdue")
                .isEqualTo(true);
        assertThat(result)
                .bodyJson()
                .extractingPath(cell(a, code(3)) + ".overdue")
                .isEqualTo(false);
    }

    @Test
    void 미래_회차_허용_반은_열렸지만_밀림이_아니다() throws Exception {
        jdbc.update("update `class` set allow_future_lessons = true where id = ?", classId);
        sessions(3, 1);
        long a = enrolled("가");

        MvcTestResult result = matrix();

        assertThat((List<Boolean>) read(result, "$.sessions[*].opened")).containsExactly(true, true, true);
        assertThat(result)
                .bodyJson()
                .extractingPath(cell(a, code(3)) + ".status")
                .isEqualTo("not_started");
        assertThat(result)
                .bodyJson()
                .extractingPath(cell(a, code(3)) + ".overdue")
                .isEqualTo(false);
    }

    @Test
    void 휴강_행은_열이_아니고_옮긴_행이_열이다() throws Exception {
        data.session(classId, 1, code(1), TODAY.minusDays(4), "done");
        data.session(classId, 2, code(2), TODAY.minusDays(2), "cancelled");
        data.session(classId, 3, code(2), TODAY, "scheduled");
        long a = enrolled("가");

        MvcTestResult result = matrix();

        assertThat((List<Integer>) read(result, "$.sessions[*].seq")).containsExactly(1, 3);
        assertThat((List<String>) read(result, "$.sessions[*].lesson.code")).containsExactly(code(1), code(2));
        assertThat((Map<String, Object>) read(result, "$.cells['" + a + "']")).containsOnlyKeys(code(1), code(2));
    }

    @Test
    void status는_done이면_done_아니면_scheduled() throws Exception {
        data.session(classId, 1, code(1), TODAY.minusDays(2), "done");
        data.session(classId, 2, code(2), TODAY, "scheduled");

        assertThat((List<String>) read(matrix(), "$.sessions[*].status")).containsExactly("done", "scheduled");
    }

    @Test
    void 오늘_회차는_학원_날짜로_정한다() throws Exception {
        clock.set(Instant.parse("2026-11-10T15:10:00Z")); // KST 11/11 00:10
        data.session(classId, 1, code(1), LocalDate.of(2026, 11, 10), "scheduled");
        data.session(classId, 2, code(2), LocalDate.of(2026, 11, 11), "scheduled");

        MvcTestResult result = matrix();

        assertThat((List<Boolean>) read(result, "$.sessions[*].isCurrent")).containsExactly(false, true);
        assertThat((List<Boolean>) read(result, "$.sessions[*].opened")).containsExactly(true, true);
    }

    @Test
    void 떠난_학생은_행이_없고_학생은_이름_순() throws Exception {
        sessions(2, 2);
        long b = enrolled("나");
        long a = enrolled("가");
        long left = enrolled("다");
        data.leave(classId, left);
        jdbc.update("update `user` set school = 'A중', grade = '중1' where id = ?", a);

        MvcTestResult result = matrix();

        assertThat((List<Integer>) read(result, "$.students[*].id")).containsExactly((int) a, (int) b);
        assertThat(result).bodyJson().extractingPath("$.students[0].school").isEqualTo("A중");
        assertThat(result).bodyJson().extractingPath("$.students[1].school").isNull();
        assertThat((Map<String, Object>) read(result, "$.cells"))
                .containsOnlyKeys(String.valueOf(a), String.valueOf(b));
    }

    @Test
    void 끝난_반도_담당이면_본다() {
        long finished = data.clazz(academyId, "L2-B1", "끝난 반", TestData.uniqueAccessCode(), "finished");
        data.assignTeacher(finished, teacherId);

        MvcTestResult result = matrix(teacherId, UserRole.TEACHER, finished, "");

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.class.name").isEqualTo("끝난 반");
    }

    @Test
    void 담당_아닌_반_다른_학원_반_없는_반은_404() {
        long notMine = data.clazz(academyId, "L2-B1", "남의 반", TestData.uniqueAccessCode(), "active");
        long otherAcademy = data.academy("다른 학원");
        long theirs = data.clazz(otherAcademy, "L2-B1", "남의 학원 반", TestData.uniqueAccessCode(), "active");
        data.assignTeacher(theirs, teacherId); // 데이터가 어긋나도 학원 경계가 먼저

        for (long id : new long[] {notMine, theirs, Long.MAX_VALUE}) {
            MvcTestResult result = matrix(teacherId, UserRole.TEACHER, id, "");
            assertThat(result).as("class " + id).hasStatus(404);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
        }
    }

    @Test
    void 교사로_바뀐_관리자의_남은_ADMIN_access는_담당_반만() {
        long demoted = data.staff(academyId, "강등된 관리자", "teacher");

        assertThat(matrix(demoted, UserRole.ADMIN, classId, "")).hasStatus(404);
    }

    @Test
    void 관리자는_담당이_아니어도_학원_반이면_본다() {
        long adminId = data.staff(academyId, "관리자", "admin");

        assertThat(matrix(adminId, UserRole.ADMIN, classId, "")).hasStatusOk();
    }

    @Test
    void 반_소속이_어긋난_다른_학원_학생은_행도_칸도_없다() throws Exception {
        sessions(2, 2);
        long mine = enrolled("우리 학생");
        long stranger = data.student(data.academy("다른 학원"), "남의 학원 학생");
        data.enroll(classId, stranger);

        MvcTestResult result = matrix();

        assertThat((List<Integer>) read(result, "$.students[*].id")).containsExactly((int) mine);
        assertThat((Map<String, Object>) read(result, "$.cells")).containsOnlyKeys(String.valueOf(mine));
    }

    @Test
    void 비활성_교사는_401() {
        jdbc.update("update `user` set status = 'inactive' where id = ?", teacherId);

        assertThat(matrix()).hasStatus(401);
    }

    @Test
    void 응답_JSON_키() throws Exception {
        sessions(2, 2);
        long a = enrolled("가");

        MvcTestResult result = matrix();

        assertThat((Map<String, Object>) read(result, "$")).containsOnlyKeys("class", "sessions", "students", "cells");
        assertThat((Map<String, Object>) read(result, "$.class")).containsOnlyKeys("id", "name", "level");
        assertThat((Map<String, Object>) read(result, "$.sessions[0]"))
                .containsOnlyKeys("seq", "lesson", "scheduledDate", "status", "isCurrent", "opened");
        assertThat((Map<String, Object>) read(result, "$.sessions[0].lesson"))
                .containsOnlyKeys("code", "number", "title");
        assertThat((Map<String, Object>) read(result, "$.students[0]"))
                .containsOnlyKeys("id", "name", "school", "grade");
        assertThat((Map<String, Object>) read(result, cell(a, code(1))))
                .containsOnlyKeys("status", "requiredDone", "requiredTotal", "completedAt", "overdue");
    }
}
