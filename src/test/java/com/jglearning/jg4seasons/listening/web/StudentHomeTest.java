package com.jglearning.jg4seasons.listening.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.MutableClock;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.auth.AuthCookieSupport;
import com.jglearning.jg4seasons.auth.UserRole;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * 2.1 GET /student/home — 오늘 수업·밀린 수업·오늘 단어·다음 수업·통계(S-03). 오늘 = 서울 2026-11-10 정오. 반 L2-B1, n회차 수업일 = 11/10 + (n - 5) × 2일
 * (1~4회차 지난 수업, 5회차 오늘, 6회차 11/12). 진도: 1·2 완료, 3 진행 중, 4 기록 없음.
 */
@IntegrationTest
@Transactional
class StudentHomeTest {

    static final String URL = "/api/v1/student/home";
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
        for (int n = 1; n <= 24; n++) {
            data.session(classId, n, code(n), DAY5.plusDays((n - 5) * 2L), "scheduled");
        }
        data.lessonContent(code(5), 2, 1, List.of("1.0"));
        data.words(code(5), 3);
        progress(1, "completed", 0, 0);
        progress(2, "completed", 0, 0);
        progress(3, "in_progress", 1, 0);
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    static String code(int n) {
        return String.format("L2-B1-%02d", n);
    }

    private void progress(int n, String status, int done, int total) {
        jdbc.update(
                "insert into lesson_progress (student_id, lesson_id, status, passages_done, passages_total)"
                        + " values (?, ?, ?, ?, ?)",
                studentId,
                data.lessonId(code(n)),
                status,
                done,
                total);
    }

    private MvcTestResult home(Cookie cookie) {
        return mvc.get().uri(URL).cookie(cookie).exchange();
    }

    private MvcTestResult home() {
        return home(cookies.studentAccess(studentId, academyId));
    }

    @Test
    void 홈은_반_오늘_밀림_다음_통계를_준다() {
        MvcTestResult result = home();

        assertThat(result).hasStatusOk();
        var json = assertThat(result).bodyJson();
        json.extractingPath("$.class.id").isEqualTo((int) classId);
        json.extractingPath("$.class.name").isEqualTo("겨울 그린 A반");
        json.extractingPath("$.class.level.colorHex").isEqualTo("#1F7A4F");
        json.extractingPath("$.class.book.code").isEqualTo("L2-B1");
        json.extractingPath("$.class.book.season").isEqualTo("겨울");
        json.extractingPath("$.class.book.label").isEqualTo("Book 1");
        json.extractingPath("$.today.seq").isEqualTo(5);
        json.extractingPath("$.today.scheduledDate").isEqualTo("2026-11-10");
        json.extractingPath("$.today.groupLabel").isEqualTo("겨울 1");
        json.extractingPath("$.today.lesson.code").isEqualTo(code(5));
        json.extractingPath("$.today.lesson.title").isEqualTo("Reading Day 5");
        json.extractingPath("$.today.lesson.passages[1].title").isEqualTo("Made-up Passage 2");
        json.extractingPath("$.next.lesson.code").isEqualTo(code(6));
        json.extractingPath("$.next.scheduledDate").isEqualTo("2026-11-12");
        json.extractingPath("$.stats.completedLessons").isEqualTo(2);
        json.extractingPath("$.stats.totalLessons").isEqualTo(24);
        json.extractingPath("$.otherClasses").isEqualTo(List.of());
    }

    @Test
    void 밀린_수업은_미참여와_멈춤_오래된_순() {
        var json = assertThat(home()).bodyJson();

        json.extractingPath("$.overdue.length()").isEqualTo(2);
        json.extractingPath("$.overdue[0].lesson.code").isEqualTo(code(3));
        json.extractingPath("$.overdue[0].kind").isEqualTo("stopped");
        json.extractingPath("$.overdue[0].progress.status").isEqualTo("in_progress");
        json.extractingPath("$.overdue[1].lesson.code").isEqualTo(code(4));
        json.extractingPath("$.overdue[1].kind").isEqualTo("not_started");
        json.extractingPath("$.stats.overdueLessons").as("overdue 길이와 같다").isEqualTo(2);
    }

    @Test
    void 기록_없는_회차_progress는_not_started_객체() {
        var json = assertThat(home()).bodyJson();

        json.extractingPath("$.today.progress.status").isEqualTo("not_started");
        json.extractingPath("$.today.progress.passagesDone").isEqualTo(0);
        json.extractingPath("$.today.progress.passagesTotal").as("지문 수").isEqualTo(2);
        json.extractingPath("$.overdue[1].progress.status").isEqualTo("not_started");
    }

    @Test
    void 오늘_단어() {
        jdbc.update(
                "insert into word_study (student_id, lesson_id, mem_known, last_viewed_at)"
                        + " values (?, ?, 2, '2026-11-10 02:00:00')",
                studentId,
                data.lessonId(code(5)));

        var json = assertThat(home()).bodyJson();

        json.extractingPath("$.words.lessonCode").isEqualTo(code(5));
        json.extractingPath("$.words.total").isEqualTo(3);
        json.extractingPath("$.words.memKnown").isEqualTo(2);
    }

    @Test
    void 서울_자정이_지나면_오늘_회차가_바뀐다() {
        clock.set(Instant.parse("2026-11-11T14:59:59Z")); // 서울 11/11 23:59:59
        assertThat(home()).bodyJson().extractingPath("$.today.lesson.code").isEqualTo(code(5));

        clock.set(Instant.parse("2026-11-11T15:00:00Z")); // 서울 11/12 00:00:00
        var json = assertThat(home()).bodyJson();
        json.extractingPath("$.today.lesson.code").isEqualTo(code(6));
        json.extractingPath("$.overdue[2].lesson.code").as("5회차가 밀림으로").isEqualTo(code(5));
        json.extractingPath("$.words.lessonCode").isEqualTo(code(6));
        json.extractingPath("$.words.total").isEqualTo(0);
    }

    @Test
    void 개강_전이면_today_null() {
        clock.set(Instant.parse("2026-10-01T03:00:00Z"));

        var json = assertThat(home()).bodyJson();

        json.extractingPath("$.today").isNull();
        json.extractingPath("$.words").isNull();
        json.extractingPath("$.overdue").isEqualTo(List.of());
        json.extractingPath("$.next.lesson.code").isEqualTo(code(1));
    }

    @Test
    void 두_반이면_otherClasses() {
        long second = data.clazz(academyId, "L2-B1", "토요 반", TestData.uniqueAccessCode(), "active");
        data.enroll(second, studentId);

        var json = assertThat(home()).bodyJson();

        json.extractingPath("$.class.id").isEqualTo((int) classId);
        json.extractingPath("$.otherClasses[0].id").isEqualTo((int) second);
        json.extractingPath("$.otherClasses[0].name").isEqualTo("토요 반");
    }

    @Test
    void 다른_학원_학생은_404() {
        long otherAcademy = data.academy("다른 학원");
        long otherStudent = data.student(otherAcademy, "박학생");

        assertThat(home(cookies.studentAccess(otherStudent, otherAcademy))).hasStatus(404);
        assertThat(home(cookies.studentAccess(studentId, otherAcademy)))
                .as("토큰 학원과 소속이 다름")
                .hasStatus(404);
    }

    @Test
    void 퇴원했으면_404() {
        data.leave(classId, studentId);

        assertThat(home()).hasStatus(404);
    }

    @Test
    void 학원이_정지면_401() {
        jdbc.update("update academy set status = 'suspended' where id = ?", academyId);

        assertThat(home()).hasStatus(401);
    }

    @Test
    void 교사는_403() {
        long teacher = data.staff(academyId, "교사", "teacher");

        assertThat(home(cookies.access(teacher, UserRole.TEACHER, academyId))).hasStatus(403);
    }
}
