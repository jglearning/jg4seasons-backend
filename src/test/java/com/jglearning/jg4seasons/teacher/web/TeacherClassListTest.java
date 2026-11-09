package com.jglearning.jg4seasons.teacher.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.MutableClock;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.auth.AuthCookieSupport;
import com.jglearning.jg4seasons.auth.LoginAttemptLimiter;
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

/** 4.1 교사 담당 반 목록(T-02). 학원 타임존 기본 Asia/Seoul, 오늘 = 2026-11-10. */
@IntegrationTest
@Transactional
class TeacherClassListTest {

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

    @Autowired
    LoginAttemptLimiter limiter;

    TestData data;
    long academyId;
    long teacherId;
    long classId;

    @BeforeEach
    void setUp() {
        clock.set(NOON_KST);
        data = new TestData(jdbc);
        academyId = data.academy("교사 목록 학원");
        teacherId = data.staff(academyId, "김교사", "teacher");
        classId = activeClass("담당 반");
        data.assignTeacher(classId, teacherId);
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    private long activeClass(String name) {
        return data.clazz(academyId, "L2-B1", name, TestData.uniqueAccessCode(), "active");
    }

    private MvcTestResult classes(long actorId, UserRole role) {
        return mvc.get()
                .uri("/api/v1/teacher/classes")
                .cookie(cookies.access(actorId, role, academyId))
                .exchange();
    }

    private MvcTestResult classes() {
        return classes(teacherId, UserRole.TEACHER);
    }

    private long enrolled(long inClass, String name) {
        long id = data.student(academyId, name);
        data.enroll(inClass, id);
        return id;
    }

    private void progress(long student, String lessonCode, String status) {
        jdbc.update(
                "insert into lesson_progress (student_id, lesson_id, status) values (?, ?, ?)",
                student,
                data.lessonId(lessonCode),
                status);
    }

    /** seq 1(11/6) · 2(오늘 11/10) · 3(11/12) */
    private void threeSessions(long inClass) {
        data.session(inClass, 1, "L2-B1-04", TODAY.minusDays(4), "done");
        data.session(inClass, 2, "L2-B1-05", TODAY, "scheduled");
        data.session(inClass, 3, "L2-B1-06", TODAY.plusDays(2), "scheduled");
    }

    private static <T> T read(MvcTestResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }

    @Test
    void 담당_반만_오늘_회차와_완료_진행_시작전_수() {
        threeSessions(classId);
        activeClass("남의 반");
        long a = enrolled(classId, "가");
        long b = enrolled(classId, "나");
        enrolled(classId, "다");
        progress(a, "L2-B1-05", "completed");
        progress(b, "L2-B1-05", "in_progress");
        String title = jdbc.queryForObject("select title from lesson where code = 'L2-B1-05'", String.class);

        MvcTestResult result = classes();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.items.length()").isEqualTo(1);
        assertThat(result).bodyJson().extractingPath("$.items[0].id").asNumber().isEqualTo((int) classId);
        assertThat(result).bodyJson().extractingPath("$.items[0].name").isEqualTo("담당 반");
        assertThat(result).bodyJson().extractingPath("$.items[0].studentCount").isEqualTo(3);
        assertThat(result).bodyJson().extractingPath("$.items[0].current.seq").isEqualTo(2);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.items[0].current.lesson.code")
                .isEqualTo("L2-B1-05");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.items[0].current.lesson.title")
                .isEqualTo(title);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.items[0].current.scheduledDate")
                .isEqualTo("2026-11-10");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.items[0].current.completed")
                .isEqualTo(1);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.items[0].current.inProgress")
                .isEqualTo(1);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.items[0].current.notStarted")
                .isEqualTo(1);
        assertThat(result).bodyJson().extractingPath("$.items[0].next.seq").isEqualTo(3);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.items[0].next.scheduledDate")
                .isEqualTo("2026-11-12");
        assertThat(result).bodyJson().extractingPath("$.items[0].level.code").isEqualTo("L2");
        assertThat(result).bodyJson().extractingPath("$.items[0].book.code").isEqualTo("L2-B1");
        assertThat(result).bodyJson().extractingPath("$.items[0].book.label").isEqualTo("Book 1");
        assertThat(result).bodyJson().extractingPath("$.items[0].loginAlert").isNull();
    }

    @Test
    void 밀린_수업이_있는_학생_수() {
        threeSessions(classId);
        long a = enrolled(classId, "가");
        long b = enrolled(classId, "나");
        long c = enrolled(classId, "다");
        progress(a, "L2-B1-04", "in_progress"); // 지난 회차 멈춤 — 밀림
        // b: 지난 회차 시작 안 함 — 밀림
        progress(c, "L2-B1-04", "completed"); // 오늘 회차만 미완료 — 밀림 아님

        assertThat(classes())
                .bodyJson()
                .extractingPath("$.items[0].overdueStudents")
                .isEqualTo(2);
    }

    @Test
    void 개강_전_반은_current_null이고_next는_첫_수업() {
        data.session(classId, 1, "L2-B1-01", TODAY.plusDays(1), "scheduled");
        enrolled(classId, "가");

        MvcTestResult result = classes();

        assertThat(result).bodyJson().extractingPath("$.items[0].current").isNull();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.items[0].overdueStudents")
                .isEqualTo(0);
        assertThat(result).bodyJson().extractingPath("$.items[0].next.seq").isEqualTo(1);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.items[0].next.scheduledDate")
                .isEqualTo("2026-11-11");
    }

    @Test
    void 진도표가_끝난_반은_next_null() {
        data.session(classId, 1, "L2-B1-01", TODAY.minusDays(2), "done");

        MvcTestResult result = classes();

        assertThat(result).bodyJson().extractingPath("$.items[0].current.seq").isEqualTo(1);
        assertThat(result).bodyJson().extractingPath("$.items[0].next").isNull();
    }

    @Test
    void 떠난_학생은_세지_않는다() {
        threeSessions(classId);
        enrolled(classId, "남음");
        long left = enrolled(classId, "떠남");
        data.leave(classId, left);

        MvcTestResult result = classes();

        assertThat(result).bodyJson().extractingPath("$.items[0].studentCount").isEqualTo(1);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.items[0].current.notStarted")
                .isEqualTo(1);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.items[0].overdueStudents")
                .isEqualTo(1);
    }

    @Test
    void 로그인_경고() {
        for (int i = 0; i < 20; i++) {
            String ip = "198.51.100." + i;
            limiter.beginAttempt(classId, ip);
            limiter.recordFailure(classId, ip);
        }

        MvcTestResult result = classes();

        assertThat(result)
                .bodyJson()
                .extractingPath("$.items[0].loginAlert.failures")
                .isEqualTo(20);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.items[0].loginAlert.since")
                .isEqualTo("2026-11-10T03:00:00Z");
    }

    @Test
    void 관리자는_학원_진행_중_반_전부() throws Exception {
        long adminId = data.staff(academyId, "관리자", "admin");
        long other = activeClass("남의 반");
        data.clazz(academyId, "L2-B1", "끝난 반", TestData.uniqueAccessCode(), "finished");

        MvcTestResult result = classes(adminId, UserRole.ADMIN);

        assertThat((List<Integer>) read(result, "$.items[*].id")).containsExactly((int) classId, (int) other);
    }

    @Test
    void 교사로_바뀐_관리자의_남은_ADMIN_access는_담당_반만() throws Exception {
        long demoted = data.staff(academyId, "강등된 관리자", "teacher");
        data.assignTeacher(classId, demoted);
        activeClass("남의 반");

        assertThat((List<Integer>) read(classes(demoted, UserRole.ADMIN), "$.items[*].id"))
                .containsExactly((int) classId);
    }

    @Test
    void 로그인_경고는_그_반_항목에만() throws Exception {
        long second = activeClass("담당 반 2");
        data.assignTeacher(second, teacherId);
        long theirs = data.clazz(data.academy("다른 학원"), "L2-B1", "남의 학원 반", TestData.uniqueAccessCode(), "active");
        for (long target : new long[] {second, theirs}) {
            for (int i = 0; i < 20; i++) {
                limiter.beginAttempt(target, "198.51.100." + i);
                limiter.recordFailure(target, "198.51.100." + i);
            }
        }

        MvcTestResult result = classes();

        assertThat((List<Integer>) read(result, "$.items[*].id")).containsExactly((int) classId, (int) second);
        assertThat(result).bodyJson().extractingPath("$.items[0].loginAlert").isNull();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.items[1].loginAlert.failures")
                .isEqualTo(20);
    }

    @Test
    void 다른_반과_다른_학원_학생의_진도는_세지_않는다() {
        threeSessions(classId);
        enrolled(classId, "우리 학생");
        long otherClass = activeClass("같은 학원 다른 반");
        threeSessions(otherClass);
        long neighbor = enrolled(otherClass, "옆 반 학생");
        long stranger = data.student(data.academy("다른 학원"), "남의 학원 학생");
        progress(neighbor, "L2-B1-05", "completed");
        progress(stranger, "L2-B1-05", "completed");

        MvcTestResult result = classes();

        assertThat(result).bodyJson().extractingPath("$.items[0].studentCount").isEqualTo(1);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.items[0].current.completed")
                .isEqualTo(0);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.items[0].current.notStarted")
                .isEqualTo(1);
    }

    @Test
    void 반_소속이_어긋난_다른_학원_학생은_세지_않는다() {
        // enrollment에는 학원 칸이 없다 — 잘못 들어간 다른 학원 학생의 진도가 우리 학원 숫자에 섞이면 안 된다
        threeSessions(classId);
        enrolled(classId, "우리 학생");
        long stranger = data.student(data.academy("다른 학원"), "남의 학원 학생");
        data.enroll(classId, stranger);
        progress(stranger, "L2-B1-04", "in_progress");
        progress(stranger, "L2-B1-05", "completed");

        MvcTestResult result = classes();

        assertThat(result).bodyJson().extractingPath("$.items[0].studentCount").isEqualTo(1);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.items[0].current.completed")
                .isEqualTo(0);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.items[0].current.notStarted")
                .isEqualTo(1);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.items[0].overdueStudents")
                .isEqualTo(1);
    }

    @Test
    void 다른_학원_반은_보이지_않는다() throws Exception {
        long otherAcademy = data.academy("다른 학원");
        long theirs = data.clazz(otherAcademy, "L2-B1", "남의 학원 반", TestData.uniqueAccessCode(), "active");
        data.assignTeacher(theirs, teacherId); // 데이터가 어긋나도 학원 경계가 먼저

        assertThat((List<Integer>) read(classes(), "$.items[*].id")).containsExactly((int) classId);
    }

    @Test
    void 비활성_교사는_401() {
        jdbc.update("update `user` set status = 'inactive' where id = ?", teacherId);

        assertThat(classes()).hasStatus(401);
    }

    @Test
    void 응답_JSON_키() throws Exception {
        threeSessions(classId);
        for (int i = 0; i < 20; i++) {
            limiter.beginAttempt(classId, "198.51.100." + i);
            limiter.recordFailure(classId, "198.51.100." + i);
        }

        MvcTestResult result = classes();

        Map<String, Object> item = read(result, "$.items[0]");
        assertThat(item)
                .containsOnlyKeys(
                        "id",
                        "name",
                        "level",
                        "book",
                        "studentCount",
                        "current",
                        "overdueStudents",
                        "loginAlert",
                        "next");
        assertThat((Map<String, Object>) read(result, "$.items[0].current"))
                .containsOnlyKeys("seq", "lesson", "scheduledDate", "completed", "inProgress", "notStarted");
        assertThat((Map<String, Object>) read(result, "$.items[0].current.lesson"))
                .containsOnlyKeys("code", "title");
        assertThat((Map<String, Object>) read(result, "$.items[0].level"))
                .containsOnlyKeys("code", "nameKo", "colorHex");
        assertThat((Map<String, Object>) read(result, "$.items[0].book")).containsOnlyKeys("code", "label", "season");
        assertThat((Map<String, Object>) read(result, "$.items[0].loginAlert")).containsOnlyKeys("failures", "since");
        assertThat((Map<String, Object>) read(result, "$.items[0].next")).containsOnlyKeys("seq", "scheduledDate");
    }
}
