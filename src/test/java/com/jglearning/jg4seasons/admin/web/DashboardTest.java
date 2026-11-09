package com.jglearning.jg4seasons.admin.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

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

/** 5.4 학원 현황(A-04): 진행 중 반별 오늘 회차·완료 비율, 오늘(학원 날짜) 들은 학생 수. 학원 타임존은 기본 Asia/Seoul. */
@IntegrationTest
@Transactional
class DashboardTest {

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
    long adminId;
    Map<String, Long> tracks;

    @BeforeEach
    void setUp() {
        clock.set(NOON_KST);
        data = new TestData(jdbc);
        academyId = data.academy("현황 학원");
        adminId = data.staff(academyId, "관리자", "admin");
        tracks = data.lessonContent("L2-B1-05", 1, 1, List.of("1.0"));
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    private long activeClass(String name) {
        return data.clazz(academyId, "L2-B1", name, TestData.uniqueAccessCode(), "active");
    }

    private MvcTestResult dashboard(long academy, long admin) {
        return mvc.get()
                .uri("/api/v1/admin/dashboard")
                .cookie(cookies.access(admin, UserRole.ADMIN, academy))
                .exchange();
    }

    private MvcTestResult dashboard() {
        return dashboard(academyId, adminId);
    }

    private void lessonProgress(long student, String lessonCode, String status) {
        jdbc.update(
                "insert into lesson_progress (student_id, lesson_id, status) values (?, ?, ?)",
                student,
                data.lessonId(lessonCode),
                status);
    }

    private void played(long student, String lastPlayedAtUtc) {
        jdbc.update(
                "insert into track_progress (student_id, track_id, merged_ranges, last_played_at)"
                        + " values (?, ?, '[]', ?)",
                student,
                tracks.get("p1:1.0"),
                LocalDateTime.parse(lastPlayedAtUtc));
    }

    private long enrolledStudent(long classId, String name) {
        long id = data.student(academyId, name);
        data.enroll(classId, id);
        return id;
    }

    private static <T> T read(MvcTestResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }

    @Test
    void 반별_오늘_회차와_완료_비율() throws Exception {
        long classId = activeClass("A반");
        data.session(classId, 1, "L2-B1-04", TODAY.minusDays(4), "scheduled");
        data.session(classId, 2, "L2-B1-05", TODAY, "scheduled");
        data.session(classId, 3, "L2-B1-06", TODAY.plusDays(2), "scheduled");
        long a = enrolledStudent(classId, "가");
        long b = enrolledStudent(classId, "나");
        long c = enrolledStudent(classId, "다");
        lessonProgress(a, "L2-B1-05", "completed");
        lessonProgress(b, "L2-B1-05", "completed");
        lessonProgress(c, "L2-B1-05", "in_progress");
        lessonProgress(c, "L2-B1-04", "completed"); // 지난 회차 완료는 세지 않음
        String title = jdbc.queryForObject("select title from lesson where code = 'L2-B1-05'", String.class);

        MvcTestResult result = dashboard();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.classes.length()").isEqualTo(1);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.classes[0].id")
                .asNumber()
                .isEqualTo((int) classId);
        assertThat(result).bodyJson().extractingPath("$.classes[0].name").isEqualTo("A반");
        assertThat(result).bodyJson().extractingPath("$.classes[0].current.seq").isEqualTo(2);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.classes[0].current.lesson.code")
                .isEqualTo("L2-B1-05");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.classes[0].current.lesson.title")
                .isEqualTo(title);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.classes[0].current.scheduledDate")
                .isEqualTo("2026-11-10");
        double rate = ((Number) read(result, "$.classes[0].completedRate")).doubleValue();
        assertThat(rate).isCloseTo(2.0 / 3, within(1e-9));
    }

    @Test
    void 개강_전_반은_current_null_비율_0() throws Exception {
        long classId = activeClass("개강 전 반");
        data.session(classId, 1, "L2-B1-01", TODAY.plusDays(1), "scheduled");
        long a = enrolledStudent(classId, "가");
        lessonProgress(a, "L2-B1-01", "completed"); // 미래 회차를 미리 끝냈어도 비율은 0

        MvcTestResult result = dashboard();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.classes[0].current").isNull();
        assertThat(((Number) read(result, "$.classes[0].completedRate")).doubleValue())
                .isZero();
    }

    @Test
    void 학생이_없는_반은_비율_0() throws Exception {
        long classId = activeClass("빈 반");
        data.session(classId, 1, "L2-B1-05", TODAY, "scheduled");

        MvcTestResult result = dashboard();

        assertThat(result).hasStatusOk();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.classes[0].current.lesson.code")
                .isEqualTo("L2-B1-05");
        assertThat(((Number) read(result, "$.classes[0].completedRate")).doubleValue())
                .isZero();
    }

    @Test
    void 끝난_반과_보관한_반은_빠진다() {
        long active = activeClass("진행 반");
        data.clazz(academyId, "L2-B1", "끝난 반", TestData.uniqueAccessCode(), "finished");
        data.clazz(academyId, "L2-B1", "보관 반", TestData.uniqueAccessCode(), "archived");

        MvcTestResult result = dashboard();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.classes.length()").isEqualTo(1);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.classes[0].id")
                .asNumber()
                .isEqualTo((int) active);
    }

    @Test
    void 반이_없으면_빈_목록과_0() {
        MvcTestResult result = dashboard();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.classes").asArray().isEmpty();
        assertThat(result).bodyJson().extractingPath("$.activeStudentsToday").isEqualTo(0);
    }

    @Test
    void 오늘_들은_학생은_학원_날짜로_센다() {
        clock.set(Instant.parse("2026-11-10T15:10:00Z")); // KST 11/11 00:10
        long afterMidnight = data.student(academyId, "자정 뒤");
        long beforeMidnight = data.student(academyId, "자정 전");
        played(afterMidnight, "2026-11-10T15:05:00"); // KST 11/11 00:05 — 오늘
        played(beforeMidnight, "2026-11-10T14:55:00"); // KST 11/10 23:55 — 어제

        MvcTestResult result = dashboard();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.activeStudentsToday").isEqualTo(1);
    }

    @Test
    void 반에_없는_학생과_퇴원생도_오늘_들었으면_센다() {
        long noClass = data.student(academyId, "반 없음");
        played(noClass, "2026-11-10T02:00:00");

        assertThat(dashboard())
                .bodyJson()
                .extractingPath("$.activeStudentsToday")
                .isEqualTo(1);
    }

    @Test
    void 다른_학원_학생의_청취와_반은_보이지_않는다() {
        long mine = activeClass("우리 반");
        long otherAcademy = data.academy("다른 학원");
        long otherAdmin = data.staff(otherAcademy, "다른 관리자", "admin");
        data.clazz(otherAcademy, "L2-B1", "남의 반", TestData.uniqueAccessCode(), "active");
        long stranger = data.student(otherAcademy, "남");
        played(stranger, "2026-11-10T02:00:00");

        MvcTestResult result = dashboard();

        assertThat(result).bodyJson().extractingPath("$.activeStudentsToday").isEqualTo(0);
        assertThat(result).bodyJson().extractingPath("$.classes.length()").isEqualTo(1);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.classes[0].id")
                .asNumber()
                .isEqualTo((int) mine);
        MvcTestResult theirs = dashboard(otherAcademy, otherAdmin);
        assertThat(theirs).bodyJson().extractingPath("$.activeStudentsToday").isEqualTo(1);
        assertThat(theirs).bodyJson().extractingPath("$.classes[0].name").isEqualTo("남의 반");
    }

    @Test
    void 비활성_관리자는_401() {
        jdbc.update("update `user` set status = 'inactive' where id = ?", adminId);

        assertThat(dashboard()).hasStatus(401);
    }

    @Test
    void 응답_JSON_키() throws Exception {
        long classId = activeClass("A반");
        data.session(classId, 1, "L2-B1-05", TODAY, "scheduled");

        MvcTestResult result = dashboard();

        Map<String, Object> body = read(result, "$");
        Map<String, Object> clazz = read(result, "$.classes[0]");
        Map<String, Object> current = read(result, "$.classes[0].current");
        Map<String, Object> lesson = read(result, "$.classes[0].current.lesson");
        assertThat(body).containsOnlyKeys("classes", "activeStudentsToday");
        assertThat(clazz).containsOnlyKeys("id", "name", "current", "completedRate");
        assertThat(current).containsOnlyKeys("seq", "lesson", "scheduledDate");
        assertThat(lesson).containsOnlyKeys("code", "title");
    }
}
