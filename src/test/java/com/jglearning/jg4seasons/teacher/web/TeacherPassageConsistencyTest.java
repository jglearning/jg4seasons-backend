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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * 지문 완료와 청취율이 학생 화면(2.3)과 교사 화면(4.2 매트릭스·4.3 학생 상세·4.4 회차 상세)에서 같은가(ADR 0034, 이슈 #62). 집계를 직접 넣지 않고 실제 청취 전송으로
 * 만든다 — 회차 집계(4.2)와 지문 집계가 서로 맞는 상태여야 비교가 뜻이 있다. 목소리마다 음원 길이가 다르다(10초·14초).
 */
@IntegrationTest
@Transactional
class TeacherPassageConsistencyTest {

    static final String CODE = "L2-B1-05";
    static final Instant NOON_KST = Instant.parse("2026-11-10T03:00:00Z");
    static final int[] TEN_SECONDS = {0, 3_000, 3_500, 6_500, 7_000, 10_000};
    static final int[] FOURTEEN_SECONDS = {0, 4_000, 5_000, 7_000, 8_000, 14_000};

    @Autowired
    MockMvcTester mvc;

    @Autowired
    AuthCookieSupport cookies;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MutableClock clock;

    long academyId;
    long teacherId;
    long classId;
    long studentId;
    Map<String, Long> tracks;

    @BeforeEach
    void setUp() {
        clock.set(NOON_KST);
        TestData data = new TestData(jdbc);
        academyId = data.academy("지문 일치 학원");
        teacherId = data.staff(academyId, "김교사", "teacher");
        classId = data.clazz(academyId, "L2-B1", "담당 반", TestData.uniqueAccessCode(), "active");
        data.assignTeacher(classId, teacherId);
        studentId = data.student(academyId, "김학생");
        data.enroll(classId, studentId);
        data.session(classId, 5, CODE, LocalDate.of(2026, 11, 10), "scheduled");
        LinkedHashMap<String, int[]> voices = new LinkedHashMap<>();
        voices.put("us_w", TEN_SECONDS);
        voices.put("uk_m", FOURTEEN_SECONDS);
        tracks = data.lessonContent(CODE, 3, voices);
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    private void flush(String trackKey, int startMs, int endMs) {
        String json = "{\"clientSessionId\":\"" + UUID.randomUUID() + "\",\"trackId\":" + tracks.get(trackKey)
                + ",\"startedAt\":\"2026-11-10T02:59:00Z\",\"userAgent\":\"test\",\"segments\":[{\"startMs\":"
                + startMs + ",\"endMs\":" + endMs + ",\"recordedAt\":\"2026-11-10T02:59:59Z\"}],\"final\":false}";
        MvcTestResult result = mvc.post()
                .uri("/api/v1/listening/flush")
                .contentType(MediaType.APPLICATION_JSON)
                .cookie(cookies.studentAccess(studentId, academyId), AuthCookieSupport.csrf())
                .header(AuthCookieSupport.CSRF_HEADER, AuthCookieSupport.CSRF_VALUE)
                .content(json)
                .exchange();
        assertThat(result).as("flush " + trackKey).hasStatusOk();
    }

    private String teacher(String path) throws Exception {
        MvcTestResult result = mvc.get()
                .uri("/api/v1/teacher/classes/" + classId + path)
                .cookie(cookies.access(teacherId, UserRole.TEACHER, academyId))
                .exchange();
        assertThat(result).as(path).hasStatusOk();
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private String studentLesson() throws Exception {
        MvcTestResult result = mvc.get()
                .uri("/api/v1/student/lessons/" + CODE)
                .cookie(cookies.studentAccess(studentId, academyId))
                .exchange();
        assertThat(result).hasStatusOk();
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    /** 지문 목록을 "번호:청취율:완료"로 — 숫자 표기(1 과 1.0)가 달라도 값으로 비교한다 */
    private static List<String> passages(String json, String path) {
        List<Map<String, Object>> rows = JsonPath.read(json, path);
        return rows.stream()
                .map(row ->
                        row.get("no") + ":" + ((Number) row.get("coverage")).doubleValue() + ":" + row.get("completed"))
                .toList();
    }

    @Test
    void 지문_완료와_청취율이_학생_화면과_교사_화면에서_같다() throws Exception {
        // 지문 1: 두 목소리로 나눠 들어 합쳐서 완료(목소리 하나로는 미완료). 지문 2: 한 목소리로 일부만. 지문 3: 안 들음
        flush("p1:us_w", 0, 5_000);
        flush("p1:uk_m", 6_000, 14_000);
        flush("p2:us_w", 0, 3_333); // 0.3333 — 교사 화면이 자릿수를 줄이면 학생 화면과 달라진다

        String student = studentLesson();
        String detail = teacher("/students/" + studentId);
        String lesson = teacher("/lessons/" + CODE);
        String matrix = teacher("/matrix");

        List<String> onStudentScreen = passages(student, "$.passages[*]");
        assertThat(onStudentScreen)
                .as("학생 화면(2.3) — 이 테스트의 전제")
                .containsExactly("1:1.0:true", "2:0.3333:false", "3:0.0:false");
        assertThat(JsonPath.<List<Boolean>>read(student, "$.passages[0].voices[*].completed"))
                .as("지문 1은 어느 목소리도 혼자서는 완료가 아니다")
                .containsOnly(false);

        // 죽여야 하는 틀린 구현: 교사 화면이 지문 값을 트랙에서 다시 계산하는 것(지문 1이 미완료·0.57로 나온다)
        assertThat(passages(detail, "$.lessons[?(@.code == '" + CODE + "')].passages[*]"))
                .as("4.3 학생 상세")
                .isEqualTo(onStudentScreen);
        assertThat(passages(lesson, "$.rows[?(@.student.id == " + studentId + ")].passages[*]"))
                .as("4.4 회차 상세")
                .isEqualTo(onStudentScreen);

        // 4.2 매트릭스의 완료 지문 수 = 4.3·4.4에서 완료인 지문 수
        int doneOnMatrix = JsonPath.read(matrix, "$.cells['" + studentId + "']['" + CODE + "'].requiredDone");
        long doneOnDetail =
                onStudentScreen.stream().filter(row -> row.endsWith(":true")).count();
        assertThat((long) doneOnMatrix)
                .as("4.2 requiredDone")
                .isEqualTo(doneOnDetail)
                .isEqualTo(1);
        List<Integer> doneOnStudentDetail =
                JsonPath.read(detail, "$.lessons[?(@.code == '" + CODE + "')].requiredDone");
        assertThat(doneOnStudentDetail).as("4.3 requiredDone").containsExactly(1);
    }
}
