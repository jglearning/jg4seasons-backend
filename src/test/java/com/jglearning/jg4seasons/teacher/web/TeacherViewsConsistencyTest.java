package com.jglearning.jg4seasons.teacher.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.MutableClock;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.auth.AuthCookieSupport;
import com.jglearning.jg4seasons.auth.UserRole;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * 교사가 오가는 세 화면 — 매트릭스(4.2) → 학생 상세(4.3) → 회차 상세(4.4) — 의 학생 목록과 (학생, 회차) 상태가 같은지. 화면마다 규칙이 갈라지면 교사가 숫자를
 * 믿지 않는다. 어긋나기 쉬운 데이터(휴강 행만 있는 회차, 진도 없는 학생, 퇴원생, 반에 섞인 다른 학원 학생)를 함께 넣는다.
 */
@IntegrationTest
@Transactional
class TeacherViewsConsistencyTest {

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
    long kim;
    long park;
    long lee;
    long foreign;

    @BeforeEach
    void setUp() {
        clock.set(NOON_KST);
        data = new TestData(jdbc);
        academyId = data.academy("일치 학원");
        teacherId = data.staff(academyId, "김교사", "teacher");
        classId = data.clazz(academyId, "L2-B1", "담당 반", TestData.uniqueAccessCode(), "active");
        data.assignTeacher(classId, teacherId);
        // 1회차 그제, 2회차 휴강 행만, 3회차 오늘, 4회차 내일
        data.session(classId, 1, "L2-B1-01", TODAY.minusDays(2), "scheduled");
        data.session(classId, 2, "L2-B1-02", TODAY.minusDays(1), "cancelled");
        data.session(classId, 3, "L2-B1-03", TODAY, "scheduled");
        data.session(classId, 4, "L2-B1-04", TODAY.plusDays(1), "scheduled");
        data.lessonContent("L2-B1-01", 2, 1, List.of("us_w"));
        data.lessonContent("L2-B1-03", 2, 1, List.of("us_w"));

        kim = data.student(academyId, "김학생");
        park = data.student(academyId, "박학생");
        lee = data.student(academyId, "이학생");
        long otherAcademy = data.academy("다른 학원");
        foreign = data.student(otherAcademy, "가학생");
        for (long s : List.of(kim, park, lee, foreign)) {
            data.enroll(classId, s);
        }
        data.leave(classId, lee);
        progress(kim, "L2-B1-01", "completed", 2, 2);
        progress(kim, "L2-B1-03", "in_progress", 1, 2);
        progress(lee, "L2-B1-01", "completed", 2, 2); // 퇴원생 진도 — 어느 화면에도 나오면 안 된다
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    private void progress(long student, String code, String status, int done, int total) {
        jdbc.update(
                "insert into lesson_progress (student_id, lesson_id, status, required_done, required_total, completed_at)"
                        + " values (?, ?, ?, ?, ?, ?)",
                student,
                data.lessonId(code),
                status,
                done,
                total,
                status.equals("completed") ? "2026-11-09 10:00:00" : null);
    }

    private MvcTestResult get(String path) {
        return mvc.get()
                .uri("/api/v1/teacher/classes/" + classId + path)
                .cookie(cookies.access(teacherId, UserRole.TEACHER, academyId))
                .exchange();
    }

    private static String body(MvcTestResult result) throws Exception {
        assertThat(result).hasStatusOk();
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void 매트릭스_학생_상세_회차_상세의_숫자가_같다() throws Exception {
        String matrix = body(get("/matrix"));
        List<Integer> matrixStudents = JsonPath.read(matrix, "$.students[*].id");
        List<String> openedCodes = JsonPath.read(matrix, "$.sessions[?(@.opened == true)].lesson.code");

        assertThat(matrixStudents).as("퇴원생·다른 학원 학생 제외").containsExactlyInAnyOrder((int) kim, (int) park);
        assertThat(openedCodes).as("휴강 행만 있는 2회차·내일 4회차 제외").containsExactly("L2-B1-01", "L2-B1-03");

        // 4.3: 4.2 학생마다 열린 회차 목록·상태·완료 수가 4.2 칸과 같다
        for (int studentId : matrixStudents) {
            String detail = body(get("/students/" + studentId));
            List<String> detailCodes = JsonPath.read(detail, "$.lessons[*].code");
            assertThat(detailCodes).as("4.3 회차 = 4.2 열린 열, 학생 " + studentId).isEqualTo(openedCodes);
            for (String code : openedCodes) {
                Map<String, Object> cell = JsonPath.read(matrix, "$.cells['" + studentId + "']['" + code + "']");
                Map<String, Object> lesson = JsonPath.<List<Map<String, Object>>>read(
                                detail, "$.lessons[?(@.code == '" + code + "')]")
                        .get(0);
                String where = "학생 " + studentId + " 회차 " + code;
                assertThat(lesson.get("status")).as(where).isEqualTo(cell.get("status"));
                assertThat(lesson.get("requiredDone")).as(where).isEqualTo(cell.get("requiredDone"));
                assertThat(lesson.get("requiredTotal")).as(where).isEqualTo(cell.get("requiredTotal"));
            }
        }

        // 4.4: 열린 회차마다 행 학생 = 4.2 학생, 행 상태 = 4.2 칸
        for (String code : openedCodes) {
            String lessonDetail = body(get("/lessons/" + code));
            List<Integer> rowStudents = JsonPath.read(lessonDetail, "$.rows[*].student.id");
            assertThat(rowStudents).as("4.4 행 학생, 회차 " + code).containsExactlyInAnyOrderElementsOf(matrixStudents);
            for (int studentId : matrixStudents) {
                String rowStatus = JsonPath.<List<String>>read(
                                lessonDetail, "$.rows[?(@.student.id == " + studentId + ")].status")
                        .get(0);
                String cellStatus = JsonPath.read(matrix, "$.cells['" + studentId + "']['" + code + "'].status");
                assertThat(rowStatus).as("학생 " + studentId + " 회차 " + code).isEqualTo(cellStatus);
            }
        }

        // 4.2에 없는 학생은 4.3도 404
        Set<Long> outside = List.of(lee, foreign).stream().collect(Collectors.toSet());
        for (long studentId : outside) {
            assertThat(get("/students/" + studentId)).as("학생 " + studentId).hasStatus(404);
        }
    }
}
