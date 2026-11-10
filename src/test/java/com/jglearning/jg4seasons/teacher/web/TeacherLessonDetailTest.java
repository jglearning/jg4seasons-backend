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
 * 4.4 회차 상세(T-05): 학생 × 트랙 표. 오늘 = 서울 2026-11-10. 반 L2-B1(교사 담당), 진도표 5회차 오늘·6회차 내일. 학생 목록은 4.2와 같다(그 반 현재
 * 소속 ∩ 그 학원 학생, 이름 순). 회차 내용은 지어낸 것.
 */
@IntegrationTest
@Transactional
class TeacherLessonDetailTest {

    static final Instant NOON_KST = Instant.parse("2026-11-10T03:00:00Z");
    static final LocalDate TODAY = LocalDate.of(2026, 11, 10);
    static final String CODE = "L2-B1-05";

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
    long park;
    long kim;
    long lee;
    Map<String, Long> tracks;

    @BeforeEach
    void setUp() {
        clock.set(NOON_KST);
        data = new TestData(jdbc);
        academyId = data.academy("회차 상세 학원");
        teacherId = data.staff(academyId, "김교사", "teacher");
        classId = data.clazz(academyId, "L2-B1", "담당 반", TestData.uniqueAccessCode(), "active");
        data.assignTeacher(classId, teacherId);
        park = data.student(academyId, "박학생");
        kim = data.student(academyId, "김학생");
        lee = data.student(academyId, "이학생");
        for (long s : List.of(park, kim, lee)) {
            data.enroll(classId, s);
        }
        data.session(classId, 5, CODE, TODAY, "scheduled");
        data.session(classId, 6, "L2-B1-06", TODAY.plusDays(1), "scheduled");
        tracks = data.lessonContent(CODE, 2, 2, List.of("us_w", "us_m"));
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    private MvcTestResult lesson(long actorId, UserRole role, long actorAcademy, long inClass, String code) {
        return mvc.get()
                .uri("/api/v1/teacher/classes/" + inClass + "/lessons/" + code)
                .cookie(cookies.access(actorId, role, actorAcademy))
                .exchange();
    }

    private MvcTestResult lesson(String code) {
        return lesson(teacherId, UserRole.TEACHER, academyId, classId, code);
    }

    @Test
    void 학생_이름_순으로_트랙별_결과를_준다() {
        long p1UsW = tracks.get("p1:us_w");
        jdbc.update(
                "insert into track_progress (student_id, track_id, merged_ranges, covered_ms, coverage, completed_at,"
                        + " total_play_ms, session_count, last_played_at) values (?, ?, '[]', 0, 0.9400, ?, 1000, 1, ?)",
                kim,
                p1UsW,
                "2026-11-10 01:00:00",
                "2026-11-10 01:00:00");
        jdbc.update(
                "insert into lesson_progress (student_id, lesson_id, status, required_done, required_total)"
                        + " values (?, ?, 'in_progress', 1, 2)",
                kim,
                data.lessonId(CODE));

        MvcTestResult result = lesson(CODE);

        assertThat(result).hasStatusOk();
        var json = assertThat(result).bodyJson();
        json.extractingPath("$.lesson.code").isEqualTo(CODE);
        json.extractingPath("$.lesson.scheduledDate").isEqualTo("2026-11-10");
        json.extractingPath("$.tracks[*].trackId")
                .isEqualTo(List.of(
                        (int) p1UsW,
                        tracks.get("p1:us_m").intValue(),
                        tracks.get("p2:us_w").intValue(),
                        tracks.get("p2:us_m").intValue()));
        json.extractingPath("$.tracks[*].variant").isEqualTo(List.of("us_w", "us_m", "us_w", "us_m"));
        json.extractingPath("$.tracks[*].passageNo").isEqualTo(List.of(1, 1, 2, 2));
        json.extractingPath("$.tracks[*].kind").isEqualTo(List.of("passage", "passage", "passage", "passage"));
        json.extractingPath("$.tracks[*].required").isEqualTo(List.of(true, true, true, true));
        json.extractingPath("$.rows[*].student.name").isEqualTo(List.of("김학생", "박학생", "이학생"));
        json.extractingPath("$.rows[0].student.id").isEqualTo((int) kim);
        json.extractingPath("$.rows[0].status").isEqualTo("in_progress");
        json.extractingPath("$.rows[0].byTrack")
                .isEqualTo(Map.of(String.valueOf(p1UsW), Map.of("coverage", 0.94, "completed", true)));
        json.extractingPath("$.rows[1].status").isEqualTo("not_started");
        json.extractingPath("$.rows[1].byTrack").isEqualTo(Map.of());
    }

    @Test
    void 수업_전_회차도_200() {
        data.lessonContent("L2-B1-06", 1, 1, List.of("us_w"));

        MvcTestResult result = lesson("L2-B1-06");

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.lesson.scheduledDate").isEqualTo("2026-11-11");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.rows[*].status")
                .isEqualTo(List.of("not_started", "not_started", "not_started"));
    }

    @Test
    void 휴강_행만_있으면_scheduledDate_null() {
        jdbc.update("update class_session set status = 'cancelled' where class_id = ? and seq = 5", classId);

        assertThat(lesson(CODE))
                .bodyJson()
                .extractingPath("$.lesson.scheduledDate")
                .isNull();
    }

    @Test
    void 퇴원한_학생과_반에_섞인_다른_학원_학생은_행에_없다() {
        data.leave(classId, lee);
        long otherAcademy = data.academy("다른 학원");
        long foreign = data.student(otherAcademy, "가학생");
        data.enroll(classId, foreign);

        assertThat(lesson(CODE))
                .bodyJson()
                .extractingPath("$.rows[*].student.name")
                .isEqualTo(List.of("김학생", "박학생"));
    }

    @Test
    void 관리자는_담당이_아니어도_200() {
        long adminId = data.staff(academyId, "관리자", "admin");

        assertThat(lesson(adminId, UserRole.ADMIN, academyId, classId, CODE)).hasStatusOk();
    }

    @Test
    void 범위_밖이면_404() {
        long otherAcademy = data.academy("다른 학원");
        long otherTeacher = data.staff(otherAcademy, "다른 교사", "teacher");
        long notMine = data.staff(academyId, "옆 반 교사", "teacher");

        assertNotFound(lesson("L3-B1-05"), "다른 권 회차");
        assertNotFound(lesson("L9-B9-99"), "없는 회차");
        assertNotFound(lesson(otherTeacher, UserRole.TEACHER, otherAcademy, classId, CODE), "다른 학원 교사");
        assertNotFound(lesson(notMine, UserRole.TEACHER, academyId, classId, CODE), "담당 아닌 교사");
    }

    private static void assertNotFound(MvcTestResult result, String why) {
        assertThat(result).as(why).hasStatus(404);
        assertThat(result).as(why).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
    }
}
