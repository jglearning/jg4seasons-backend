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
                        item(1, "L2-B1-01", "2026-11-05", "scheduled", null),
                        item(2, "L2-B1-02", "2026-11-10", "cancelled", "내신 휴강"),
                        item(3, "L2-B1-03", "2026-11-12", "scheduled", null),
                        item(4, "L2-B1-02", "2026-11-17", "scheduled", null)));
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
