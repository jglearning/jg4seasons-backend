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
 * 2.3 GET /student/lessons/{lessonCode} — 회차 상세(S-05). 오늘 = 학원 타임존(서울) 날짜 2026-11-10. 반은 L2-B1, 진도표 5회차 11/10(오늘)·6회차
 * 11/11(내일). 다른 학원·다른 권·퇴원은 404, 미래 회차는 423.
 */
@IntegrationTest
@Transactional
class StudentLessonTest {

    static final String URL = "/api/v1/student/lessons/";
    static final String TODAY_CODE = "L2-B1-05";
    static final String TOMORROW_CODE = "L2-B1-06";
    /** 서울 2026-11-10 12:00 */
    static final Instant NOON_NOV_10 = Instant.parse("2026-11-10T03:00:00Z");

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
    Map<String, Long> tracks;

    @BeforeEach
    void setUp() {
        clock.set(NOON_NOV_10);
        data = new TestData(jdbc);
        academyId = data.academy("테스트 학원");
        classId = data.clazz(academyId, "L2-B1", "겨울 그린 A반", TestData.uniqueAccessCode(), "active");
        studentId = data.student(academyId, "김학생");
        data.enroll(classId, studentId);
        data.session(classId, 5, TODAY_CODE, LocalDate.of(2026, 11, 10), "scheduled");
        data.session(classId, 6, TOMORROW_CODE, LocalDate.of(2026, 11, 11), "scheduled");
        tracks = data.lessonContent(TODAY_CODE, 2, 3, List.of("0.8", "1.0"));
        data.lessonContent(TOMORROW_CODE, 1, 1, List.of("1.0"));
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    private MvcTestResult get(String code, Cookie... cookieList) {
        return mvc.get().uri(URL + code).cookie(cookieList).exchange();
    }

    private MvcTestResult getAsStudent(String code) {
        return get(code, cookies.studentAccess(studentId, academyId));
    }

    @Test
    void 열린_회차의_지문_문장_속도_시각을_받는다() {
        MvcTestResult result = getAsStudent(TODAY_CODE);

        assertThat(result).hasStatusOk();
        var json = assertThat(result).bodyJson();
        json.extractingPath("$.lesson.code").isEqualTo(TODAY_CODE);
        json.extractingPath("$.lesson.number").isEqualTo(5);
        json.extractingPath("$.lesson.title").isEqualTo("Reading Day 5");
        json.extractingPath("$.lesson.type").isEqualTo("regular");
        json.extractingPath("$.level.code").isEqualTo("L2");
        json.extractingPath("$.level.nameKo").isEqualTo("그린");
        json.extractingPath("$.level.colorHex").isEqualTo("#1F7A4F");
        json.extractingPath("$.passages.length()").isEqualTo(2);
        json.extractingPath("$.passages[0].no").isEqualTo(1);
        json.extractingPath("$.passages[0].title").isEqualTo("Made-up Passage 1");
        json.extractingPath("$.passages[0].wordCount").isEqualTo(101);
        json.extractingPath("$.passages[0].sentences[0].no").isEqualTo(1);
        json.extractingPath("$.passages[0].sentences[0].text").isEqualTo("Passage 1 sentence 1 is a made-up line.");
        json.extractingPath("$.passages[0].sentences[0].speaker").isNull();
        json.extractingPath("$.passages[0].sentences[1].speaker").isEqualTo("A");
        json.extractingPath("$.passages[0].speeds[0].variant").isEqualTo("0.8");
        json.extractingPath("$.passages[0].speeds[0].required").isEqualTo(false);
        json.extractingPath("$.passages[0].speeds[0].timingsApproximate").isEqualTo(true);
        json.extractingPath("$.passages[0].speeds[1].trackId")
                .isEqualTo(tracks.get("p1:1.0").intValue());
        json.extractingPath("$.passages[0].speeds[1].variant").isEqualTo("1.0");
        json.extractingPath("$.passages[0].speeds[1].required").isEqualTo(true);
        json.extractingPath("$.passages[0].speeds[1].audioUrl").isEqualTo("/audio/L2-B1-05/L2-B1-05_p1_1.0.mp3");
        json.extractingPath("$.passages[0].speeds[1].durationMs").isEqualTo(10000);
        json.extractingPath("$.passages[0].speeds[1].timings[0]").isEqualTo(List.of(0, 3000));
        json.extractingPath("$.passages[0].speeds[1].timings[2]").isEqualTo(List.of(7000, 10000));
        json.extractingPath("$.passages[0].speeds[1].timingsApproximate").isEqualTo(false);
        json.extractingPath("$.completion.threshold").isEqualTo(0.9);
    }

    @Test
    void 청취_기록이_없으면_0과_빈_구간() {
        MvcTestResult result = getAsStudent(TODAY_CODE);

        var json = assertThat(result).bodyJson();
        json.extractingPath("$.passages[0].speeds[1].coverage").isEqualTo(0);
        json.extractingPath("$.passages[0].speeds[1].completed").isEqualTo(false);
        json.extractingPath("$.passages[0].speeds[1].heardRanges").isEqualTo(List.of());
        json.extractingPath("$.completion.status").isEqualTo("not_started");
        json.extractingPath("$.completion.passagesDone").isEqualTo(0);
        json.extractingPath("$.completion.passagesTotal").isEqualTo(2);
        json.extractingPath("$.words.total").isEqualTo(0);
        json.extractingPath("$.words.memKnown").isEqualTo(0);
    }

    @Test
    void 청취_집계가_있으면_그대로() {
        long lessonId = data.lessonId(TODAY_CODE);
        jdbc.update(
                "insert into track_progress (student_id, track_id, merged_ranges, covered_ms, coverage, completed_at,"
                        + " last_played_at) values (?, ?, '[[0,5200],[5600,9600]]', 9200, 0.9200,"
                        + " '2026-11-10 02:00:00', '2026-11-10 02:00:00')",
                studentId,
                tracks.get("p1:1.0"));
        jdbc.update(
                "insert into lesson_progress (student_id, lesson_id, status, passages_total, passages_done)"
                        + " values (?, ?, 'in_progress', 2, 1)",
                studentId,
                lessonId);
        data.words(TODAY_CODE, 3);
        jdbc.update(
                "insert into word_study (student_id, lesson_id, mem_known, last_viewed_at)"
                        + " values (?, ?, 2, '2026-11-10 02:00:00')",
                studentId,
                lessonId);

        var json = assertThat(getAsStudent(TODAY_CODE)).bodyJson();

        json.extractingPath("$.passages[0].speeds[1].coverage").isEqualTo(0.92);
        json.extractingPath("$.passages[0].speeds[1].completed").isEqualTo(true);
        json.extractingPath("$.passages[0].speeds[1].heardRanges")
                .isEqualTo(List.of(List.of(0, 5200), List.of(5600, 9600)));
        json.extractingPath("$.passages[1].speeds[1].completed").isEqualTo(false);
        json.extractingPath("$.completion.status").isEqualTo("in_progress");
        json.extractingPath("$.completion.passagesDone").isEqualTo(1);
        json.extractingPath("$.completion.passagesTotal").isEqualTo(2);
        json.extractingPath("$.words.total").isEqualTo(3);
        json.extractingPath("$.words.memKnown").isEqualTo(2);
    }

    @Test
    void 내일_회차는_423_LESSON_LOCKED() {
        MvcTestResult result = getAsStudent(TOMORROW_CODE);

        assertThat(result).hasStatus(423);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("LESSON_LOCKED");
    }

    @Test
    void 서울_자정_전후() {
        clock.set(Instant.parse("2026-11-10T14:59:59Z")); // 서울 11/10 23:59:59
        assertThat(getAsStudent(TOMORROW_CODE)).as("자정 전").hasStatus(423);

        clock.set(Instant.parse("2026-11-10T15:00:00Z")); // 서울 11/11 00:00:00
        assertThat(getAsStudent(TOMORROW_CODE)).as("자정").hasStatusOk();
    }

    @Test
    void 다른_학원_학생은_404() {
        long otherAcademy = data.academy("다른 학원");
        long otherClass = data.clazz(otherAcademy, "L2-B1", "다른 반", TestData.uniqueAccessCode(), "active");
        long otherStudent = data.student(otherAcademy, "박학생");
        data.enroll(otherClass, otherStudent);

        assertThat(get(TODAY_CODE, cookies.studentAccess(otherStudent, otherAcademy)))
                .as("자기 학원 반에 진도표가 없음")
                .hasStatus(404);
        assertThat(get(TODAY_CODE, cookies.studentAccess(studentId, otherAcademy)))
                .as("토큰의 학원과 학생 소속이 다름")
                .hasStatus(404);
    }

    @Test
    void 다른_권_반_학생은_404() {
        long l3Class = data.clazz(academyId, "L3-B1", "블루 반", TestData.uniqueAccessCode(), "active");
        long l3Student = data.student(academyId, "최학생");
        data.enroll(l3Class, l3Student);
        data.session(l3Class, 5, "L3-B1-05", LocalDate.of(2026, 11, 10), "scheduled");

        assertThat(get(TODAY_CODE, cookies.studentAccess(l3Student, academyId))).hasStatus(404);
    }

    @Test
    void 두_번째_반_권의_회차도_열린다() {
        long second = data.clazz(academyId, "L2-B2", "봄 그린 반", TestData.uniqueAccessCode(), "active");
        data.enroll(second, studentId);
        data.session(second, 1, "L2-B2-01", LocalDate.of(2026, 11, 10), "scheduled");
        data.lessonContent("L2-B2-01", 1, 1, List.of("1.0"));

        MvcTestResult result = getAsStudent("L2-B2-01");

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.lesson.code").isEqualTo("L2-B2-01");
    }

    @Test
    void 같은_권_두_반_중_한_반에서_열렸으면_열린다() {
        long second = data.clazz(academyId, "L2-B1", "토요 반", TestData.uniqueAccessCode(), "active");
        data.enroll(second, studentId);
        data.session(second, 6, TOMORROW_CODE, LocalDate.of(2026, 11, 9), "scheduled");

        assertThat(getAsStudent(TOMORROW_CODE)).as("첫 반에서는 내일, 둘째 반에서는 어제").hasStatusOk();
    }

    @Test
    void 다른_학원_반의_진도표로는_열리지_않는다() {
        long otherAcademy = data.academy("다른 학원");
        long other = data.clazz(otherAcademy, "L2-B2", "다른 학원 반", TestData.uniqueAccessCode(), "active");
        data.enroll(other, studentId);
        data.session(other, 1, "L2-B2-01", LocalDate.of(2026, 11, 10), "scheduled");
        data.lessonContent("L2-B2-01", 1, 1, List.of("1.0"));

        assertThat(getAsStudent("L2-B2-01")).hasStatus(404);
    }

    @Test
    void 퇴원한_학생은_404() {
        data.leave(classId, studentId);

        assertThat(getAsStudent(TODAY_CODE)).hasStatus(404);
    }

    @Test
    void 없는_회차는_404() {
        assertThat(getAsStudent("L9-B9-99")).hasStatus(404);
    }

    @Test
    void 형식이_틀린_코드는_400_lessonCode() {
        MvcTestResult result = getAsStudent("L2_B1_05");

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION");
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("lessonCode");
    }

    @Test
    void 쿠키가_없으면_401() {
        assertThat(mvc.get().uri(URL + TODAY_CODE).exchange()).hasStatus(401);
    }

    @Test
    void 교사는_403() {
        long teacherId = data.staff(academyId, "교사", "teacher");

        assertThat(get(TODAY_CODE, cookies.access(teacherId, UserRole.TEACHER, academyId)))
                .hasStatus(403);
    }

    @Test
    void 학원이_정지면_401() {
        jdbc.update("update academy set status = 'suspended' where id = ?", academyId);

        assertThat(getAsStudent(TODAY_CODE)).hasStatus(401);
    }

    @Test
    void 문장_응답은_번호_원문_화자만() {
        MvcTestResult result = getAsStudent(TODAY_CODE);

        assertThat(result)
                .bodyJson()
                .extractingPath("$.passages[0].sentences[0]")
                .asMap()
                .containsOnlyKeys("no", "text", "speaker");
    }

    @Test
    void 집계의_지문_수가_실제와_다르면_실제_지문_수를_쓴다() {
        jdbc.update(
                "insert into lesson_progress (student_id, lesson_id, status, passages_total, passages_done)"
                        + " values (?, ?, 'in_progress', 6, 5)",
                studentId,
                data.lessonId(TODAY_CODE));

        var json = assertThat(getAsStudent(TODAY_CODE)).bodyJson();

        json.extractingPath("$.completion.passagesTotal").isEqualTo(2);
        json.extractingPath("$.completion.passagesDone").as("전체를 넘지 않음").isEqualTo(2);
    }

    @Test
    void 학원_타임존이_UTC면_UTC_날짜로_연다() {
        jdbc.update("update academy set settings = '{\"timezone\":\"UTC\"}' where id = ?", academyId);
        clock.set(Instant.parse("2026-11-10T15:00:00Z")); // 서울은 11/11 00:00, UTC는 아직 11/10

        assertThat(getAsStudent(TOMORROW_CODE)).hasStatus(423);
    }

    @Test
    void 휴강만_있는_회차는_423() {
        data.lessonContent("L2-B1-03", 1, 1, List.of("1.0"));
        data.session(classId, 3, "L2-B1-03", LocalDate.of(2026, 11, 5), "cancelled");

        MvcTestResult result = getAsStudent("L2-B1-03");

        assertThat(result).hasStatus(423);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("LESSON_LOCKED");
    }

    @Test
    void 반이_finished면_404() {
        jdbc.update("update `class` set status = 'finished' where id = ?", classId);

        assertThat(getAsStudent(TODAY_CODE)).hasStatus(404);
    }
}
