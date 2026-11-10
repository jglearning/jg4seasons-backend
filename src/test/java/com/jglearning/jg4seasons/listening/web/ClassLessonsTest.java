package com.jglearning.jg4seasons.listening.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.MutableClock;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.auth.AuthCookieSupport;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * 2.2 GET /student/classes/{classId}/lessons — 시즌 묶음별 회차와 상태(S-04). 오늘 = 서울 2026-11-10 정오. n회차 수업일 = 11/10 + (n - 5) × 2일. 진도: 1·2
 * 완료, 3 진행 중. 내가 소속된 active 반만(결정 F), 아니면 404.
 */
@IntegrationTest
@Transactional
class ClassLessonsTest {

    static final Instant NOON = Instant.parse("2026-11-10T03:00:00Z");
    static final LocalDate DAY5 = LocalDate.of(2026, 11, 10);

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

    @BeforeEach
    void setUp() {
        clock.set(NOON);
        data = new TestData(jdbc);
        academyId = data.academy("테스트 학원");
        classId = data.clazz(academyId, "L2-B1", "겨울 그린 A반", TestData.uniqueAccessCode(), "active");
        studentId = data.student(academyId, "김학생");
        data.enroll(classId, studentId);
        sessions(classId);
        for (int n = 1; n <= 3; n++) {
            jdbc.update(
                    "insert into lesson_progress (student_id, lesson_id, status, passages_done, passages_total)"
                            + " values (?, ?, ?, 0, 0)",
                    studentId,
                    data.lessonId(code(n)),
                    n <= 2 ? "completed" : "in_progress");
        }
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    static String code(int n) {
        return String.format("L2-B1-%02d", n);
    }

    private void sessions(long clazz) {
        for (int n = 1; n <= 24; n++) {
            data.session(clazz, n, code(n), DAY5.plusDays((n - 5) * 2L), "scheduled");
        }
    }

    private MvcTestResult lessons(long clazz, Cookie cookie) {
        return mvc.get()
                .uri("/api/v1/student/classes/" + clazz + "/lessons")
                .cookie(cookie)
                .exchange();
    }

    private MvcTestResult lessons(long clazz) {
        return lessons(clazz, cookies.studentAccess(studentId, academyId));
    }

    @Test
    void 시즌_묶음별_회차와_상태() {
        MvcTestResult result = lessons(classId);

        assertThat(result).hasStatusOk();
        var json = assertThat(result).bodyJson();
        json.extractingPath("$.groups.length()").isEqualTo(3);
        json.extractingPath("$.groups[0].module").isEqualTo(1);
        json.extractingPath("$.groups[0].label").isEqualTo("겨울 1");
        json.extractingPath("$.groups[0].range.from").isEqualTo("2026-11-02");
        json.extractingPath("$.groups[0].range.to").isEqualTo("2026-11-16");
        json.extractingPath("$.groups[1].range.from").isEqualTo("2026-11-18");
        json.extractingPath("$.groups[1].range.to").isEqualTo("2026-12-02");
        json.extractingPath("$.groups[2].label").isEqualTo("겨울 3");
        json.extractingPath("$.groups[2].range.from").isEqualTo("2026-12-04");
        json.extractingPath("$.groups[2].range.to").isEqualTo("2026-12-18");
        json.extractingPath("$.groups[0].lessons.length()").isEqualTo(8);
        json.extractingPath("$.groups[0].lessons[0].code").isEqualTo(code(1));
        json.extractingPath("$.groups[0].lessons[0].state").isEqualTo("completed");
        json.extractingPath("$.groups[0].lessons[2].state").isEqualTo("overdue");
        json.extractingPath("$.groups[0].lessons[2].progress.status").isEqualTo("in_progress");
        json.extractingPath("$.groups[0].lessons[3].state").isEqualTo("overdue");
        json.extractingPath("$.groups[0].lessons[4].state").isEqualTo("today");
        json.extractingPath("$.groups[0].lessons[4].scheduledDate").isEqualTo("2026-11-10");
        json.extractingPath("$.groups[0].lessons[5].state").isEqualTo("locked");
        json.extractingPath("$.groups[0].lessons[5].progress.status").isEqualTo("not_started");
        json.extractingPath("$.groups[0].lessons[7].type").isEqualTo("module_review");
    }

    @Test
    void class_정보는_홈과_같다() {
        var json = assertThat(lessons(classId)).bodyJson();

        json.extractingPath("$.class.id").isEqualTo((int) classId);
        json.extractingPath("$.class.name").isEqualTo("겨울 그린 A반");
        json.extractingPath("$.class.level.colorHex").isEqualTo("#1F7A4F");
        json.extractingPath("$.class.book.label").isEqualTo("Book 1");
    }

    @Test
    void 사용권을_적용하지_않는_학원_반은_licenseStatus_active() {
        assertThat(lessons(classId))
                .bodyJson()
                .extractingPath("$.class.licenseStatus")
                .isEqualTo("active");
    }

    @Test
    void 사용_기간이_끝난_권의_반은_licenseStatus_expired() {
        data.enforceLicenses(academyId);
        data.license(academyId, "L2-B1", DAY5.minusDays(60), DAY5.minusDays(1), 30);

        assertThat(lessons(classId))
                .bodyJson()
                .extractingPath("$.class.licenseStatus")
                .isEqualTo("expired");
    }

    @Test
    void 내가_속한_다른_반은_200() {
        long second = data.clazz(academyId, "L2-B1", "토요 반", TestData.uniqueAccessCode(), "active");
        data.enroll(second, studentId);
        sessions(second);

        MvcTestResult result = lessons(second);

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.class.name").isEqualTo("토요 반");
    }

    @Test
    void 같은_학원_남의_반은_404() {
        long other = data.clazz(academyId, "L2-B1", "남의 반", TestData.uniqueAccessCode(), "active");
        sessions(other);

        assertThat(lessons(other)).hasStatus(404);
    }

    @Test
    void 다른_학원_반은_404() {
        long otherAcademy = data.academy("다른 학원");
        long other = data.clazz(otherAcademy, "L2-B1", "다른 학원 반", TestData.uniqueAccessCode(), "active");
        data.enroll(other, studentId);

        assertThat(lessons(other)).as("토큰 학원과 반 학원이 다름").hasStatus(404);
    }

    @Test
    void 퇴원한_반은_404() {
        data.leave(classId, studentId);

        assertThat(lessons(classId)).hasStatus(404);
    }

    @Test
    void 미래_허용_반은_수업일_전_회차가_open() {
        jdbc.update("update `class` set allow_future_lessons = true where id = ?", classId);

        var json = assertThat(lessons(classId)).bodyJson();

        json.extractingPath("$.groups[0].lessons[4].state").as("오늘은 그대로").isEqualTo("today");
        json.extractingPath("$.groups[0].lessons[5].state").isEqualTo("open");
        json.extractingPath("$.groups[2].lessons[7].state").isEqualTo("open");
    }

    @Test
    void 휴강만_있는_회차는_locked() {
        jdbc.update(
                "update class_session set status = 'cancelled' where class_id = ? and lesson_id = ?",
                classId,
                data.lessonId(code(4)));

        var json = assertThat(lessons(classId)).bodyJson();

        json.extractingPath("$.groups[0].lessons[3].state").isEqualTo("locked");
    }

    @Test
    void 휴강으로_밀리면_회차는_번호_순이고_수업일은_새_날짜() {
        StudentHomeTest.cancelShift(data, jdbc, classId);

        var json = assertThat(lessons(classId)).bodyJson();

        json.extractingPath("$.groups[0].lessons.length()").isEqualTo(8);
        json.extractingPath("$.groups[0].lessons[3].code").isEqualTo(code(4));
        json.extractingPath("$.groups[0].lessons[3].number").isEqualTo(4);
        json.extractingPath("$.groups[0].lessons[3].state").isEqualTo("today");
        json.extractingPath("$.groups[0].lessons[3].scheduledDate")
                .as("seq 5의 날짜")
                .isEqualTo("2026-11-10");
        json.extractingPath("$.groups[0].lessons[4].state").isEqualTo("locked");
        json.extractingPath("$.groups[0].range.to").as("8회차 = seq 9").isEqualTo("2026-11-18");
        json.extractingPath("$.groups[2].lessons[7].scheduledDate")
                .as("24회차 = seq 25")
                .isEqualTo("2026-12-20");
    }

    @Test
    void 형식이_틀린_반_id는_400() {
        MvcTestResult result = mvc.get()
                .uri("/api/v1/student/classes/abc/lessons")
                .cookie(cookies.studentAccess(studentId, academyId))
                .exchange();

        assertThat(result).hasStatus(400);
    }
}
