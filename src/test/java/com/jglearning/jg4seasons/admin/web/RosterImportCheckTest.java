package com.jglearning.jg4seasons.admin.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.MutableClock;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.auth.AuthCookieSupport;
import com.jglearning.jg4seasons.auth.UserRole;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.LocalDate;
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
 * 5.5 명단 가져오기 검사(ADR 0036): 형식이 맞는 요청의 데이터 문제를 200 목록으로 모은다. 아무것도 만들지 않는다. 오늘 = 서울 2026-11-10. 요청 안만 보는 규칙은
 * RosterImportRulesTest.
 */
@IntegrationTest
@Transactional
class RosterImportCheckTest {

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
    EntityManager entityManager;

    TestData data;
    long academyId;
    long adminId;
    long teacherId;
    /** 기존 반 「그린 월수반」(L2-B1, active) */
    long greenId;

    @BeforeEach
    void setUp() {
        clock.set(NOON_KST);
        data = new TestData(jdbc);
        academyId = data.academy("가져오기 학원");
        adminId = data.staff(academyId, "관리자", "admin");
        teacherId = data.staff(academyId, "교사", "teacher");
        greenId = data.clazz(academyId, "L2-B1", "그린 월수반", TestData.uniqueAccessCode(), "active");
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    private MvcTestResult check(String classesJson, Cookie... auth) {
        Cookie[] cookie = auth.length > 0
                ? auth
                : new Cookie[] {cookies.access(adminId, UserRole.ADMIN, academyId), AuthCookieSupport.csrf()};
        return mvc.post()
                .uri("/api/v1/admin/roster-imports/check")
                .cookie(cookie)
                .header(AuthCookieSupport.CSRF_HEADER, AuthCookieSupport.CSRF_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"classes\":" + classesJson + "}")
                .exchange();
    }

    private String newClass(String name, String bookCode, String teacherIds) {
        return "{\"name\":\"" + name + "\",\"bookCode\":\"" + bookCode
                + "\",\"startDate\":\"2026-12-01\",\"weekdays\":[2,4],\"teacherIds\":" + teacherIds + "}";
    }

    private String studentsJson(String prefix, int count) {
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < count; i++) {
            out.append(i == 0 ? "" : ",")
                    .append("{\"name\":\"")
                    .append(prefix)
                    .append(i)
                    .append("\"}");
        }
        return out.append("]").toString();
    }

    private int count(String sql) {
        Integer n = jdbc.queryForObject(sql, Integer.class);
        return n == null ? 0 : n;
    }

    @Test
    void 오류가_없으면_빈_목록이고_아무것도_만들지_않는다() {
        int classes = count("select count(*) from class");
        int users = count("select count(*) from `user`");

        MvcTestResult result = check("[{\"newClass\":" + newClass("블루 화목반", "L3-B1", "[" + teacherId + "]")
                + ",\"students\":[{\"name\":\"이서연\",\"school\":\"A중\",\"grade\":\"중1\"}]},"
                + "{\"existingClassId\":" + greenId + ",\"students\":[{\"name\":\"김민준\"}]}]");

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.errors").asArray().isEmpty();
        assertThat(result).bodyJson().extractingPath("$.warnings").asArray().isEmpty();
        assertThat(count("select count(*) from class")).isEqualTo(classes);
        assertThat(count("select count(*) from `user`")).isEqualTo(users);
    }

    @Test
    void 다른_학원_반과_교사는_없는_것과_같다() throws Exception {
        long other = data.academy("다른 학원");
        long otherClass = data.clazz(other, "L2-B1", "다른 학원 비밀반", TestData.uniqueAccessCode(), "active");
        long otherTeacher = data.staff(other, "다른 교사", "teacher");

        MvcTestResult result = check("[{\"existingClassId\":" + otherClass + ",\"students\":[{\"name\":\"김민준\"}]},"
                + "{\"newClass\":" + newClass("새 반", "L2-B1", "[" + otherTeacher + "]")
                + ",\"students\":[{\"name\":\"이서연\"}]}]");

        assertThat(result).hasStatusOk();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[*].field")
                .asArray()
                .containsExactly("classes[0].existingClassId", "classes[1].newClass.teacherIds");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[*].code")
                .asArray()
                .containsExactly("NOT_FOUND", "NOT_FOUND");
        assertThat(result.getResponse().getContentAsString()).doesNotContain("다른 학원 비밀반");
    }

    @Test
    void 끝난_반과_비활성_교사는_오류() {
        long finished = data.clazz(academyId, "L2-B1", "지난 반", TestData.uniqueAccessCode(), "finished");
        long quit = data.staff(academyId, "그만둔 교사", "teacher");
        jdbc.update("update `user` set status = 'inactive' where id = ?", quit);

        MvcTestResult result = check("[{\"existingClassId\":" + finished + ",\"students\":[{\"name\":\"김민준\"}]},"
                + "{\"newClass\":" + newClass("새 반", "L2-B1", "[" + teacherId + "," + quit + "]")
                + ",\"students\":[{\"name\":\"이서연\"}]}]");

        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[*].code")
                .asArray()
                .containsExactly("CLASS_NOT_ACTIVE", "NOT_FOUND");
    }

    @Test
    void 새_반의_권_이름_요일은_반_만들기와_같은_규칙() {
        MvcTestResult result = check("[{\"newClass\":" + newClass("없는 권 반", "L9-B9", "[]")
                + ",\"students\":[{\"name\":\"김민준\"}]},"
                + "{\"newClass\":{\"name\":\"  \",\"bookCode\":\"L2-B1\",\"startDate\":\"2026-12-01\",\"weekdays\":[2,2],\"teacherIds\":[]}"
                + ",\"students\":[{\"name\":\"이서연\"}]}]");

        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[*].field")
                .asArray()
                .containsExactly(
                        "classes[0].newClass.bookCode", "classes[1].newClass.name", "classes[1].newClass.weekdays");
        assertThat(result).bodyJson().extractingPath("$.errors[0].message").isEqualTo("없는 권이에요");
    }

    @Test
    void 같은_권_두_반의_합이_한도를_넘으면_넘는_반에_오류() {
        data.enforceLicenses(academyId);
        data.license(academyId, "L2-B1", TODAY.minusDays(9), TODAY.plusDays(60), 3);
        data.enroll(greenId, data.student(academyId, "이미 다니는 학생"));

        // 지금 1명 + 1명 = 2 → 통과, + 2명 = 4 > 3 → 두 번째 반 항목
        MvcTestResult result = check("[{\"existingClassId\":" + greenId + ",\"students\":" + studentsJson("가", 1) + "},"
                + "{\"newClass\":" + newClass("그린 화목반", "L2-B1", "[]") + ",\"students\":" + studentsJson("나", 2)
                + "}]");

        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[*].field")
                .asArray()
                .containsExactly("classes[1]");
        assertThat(result).bodyJson().extractingPath("$.errors[0].code").isEqualTo("LICENSE_SEATS_FULL");
    }

    @Test
    void 한도에_딱_맞으면_통과() {
        data.enforceLicenses(academyId);
        data.license(academyId, "L2-B1", TODAY.minusDays(9), TODAY.plusDays(60), 3);
        data.enroll(greenId, data.student(academyId, "이미 다니는 학생"));

        MvcTestResult result = check("[{\"existingClassId\":" + greenId + ",\"students\":" + studentsJson("가", 1) + "},"
                + "{\"newClass\":" + newClass("그린 화목반", "L2-B1", "[]") + ",\"students\":" + studentsJson("나", 1)
                + "}]");

        assertThat(result).bodyJson().extractingPath("$.errors").asArray().isEmpty();
    }

    @Test
    void 기간_밖_권은_그_권의_반마다_LICENSE_INACTIVE() {
        data.enforceLicenses(academyId);
        data.license(academyId, "L2-B1", TODAY.minusDays(60), TODAY.minusDays(1), 30);
        data.license(academyId, "L3-B1", TODAY.minusDays(9), TODAY.plusDays(60), 30);

        MvcTestResult result = check("[{\"existingClassId\":" + greenId + ",\"students\":" + studentsJson("가", 1) + "},"
                + "{\"newClass\":" + newClass("블루반", "L3-B1", "[]") + ",\"students\":" + studentsJson("나", 1) + "},"
                + "{\"newClass\":" + newClass("그린 화목반", "L2-B1", "[]") + ",\"students\":" + studentsJson("다", 1)
                + "}]");

        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[*].field")
                .asArray()
                .containsExactly("classes[0]", "classes[2]");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[*].code")
                .asArray()
                .containsExactly("LICENSE_INACTIVE", "LICENSE_INACTIVE");
    }

    @Test
    void 영문_이름은_대소문자가_달라도_같은_이름_경고() {
        // 학생 표 정렬(utf8mb4_0900_ai_ci)이 대소문자를 구분하지 않으므로 경고도 같은 기준
        data.enroll(greenId, data.student(academyId, "Alex Kim"));

        MvcTestResult result = check("[{\"existingClassId\":" + greenId + ",\"students\":[{\"name\":\"alex kim\"}]}]");

        assertThat(result)
                .bodyJson()
                .extractingPath("$.warnings[*].code")
                .asArray()
                .containsExactly("SAME_NAME_STUDENT");
    }

    @Test
    void 이_명단_안에서_새_반_이름이_겹치면_두_번째부터_경고() {
        MvcTestResult result = check("[{\"newClass\":" + newClass("블루 화목반", "L3-B1", "[]")
                + ",\"students\":[{\"name\":\"가\"}]},"
                + "{\"newClass\":" + newClass(" 블루 화목반 ", "L3-B1", "[]") + ",\"students\":[{\"name\":\"나\"}]}]");

        assertThat(result).bodyJson().extractingPath("$.errors").asArray().isEmpty();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.warnings[*].field")
                .asArray()
                .containsExactly("classes[1].newClass.name");
        assertThat(result).bodyJson().extractingPath("$.warnings[0].message").isEqualTo("이 명단에 같은 이름의 반이 또 있어요");
    }

    @Test
    void 같은_이름_학생과_반은_경고만() {
        data.enroll(greenId, data.student(academyId, "김민준"));
        long gone = data.student(academyId, "퇴원생");
        jdbc.update("update `user` set status = 'inactive' where id = ?", gone);

        MvcTestResult result = check("[{\"newClass\":" + newClass("그린 월수반", "L2-B1", "[]")
                + ",\"students\":[{\"name\":\" 김민준 \"},{\"name\":\"퇴원생\"}]}]");

        assertThat(result).bodyJson().extractingPath("$.errors").asArray().isEmpty();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.warnings[*].field")
                .asArray()
                .containsExactly("classes[0].newClass.name", "classes[0].students[0].name");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.warnings[*].code")
                .asArray()
                .containsExactly("SAME_NAME_CLASS", "SAME_NAME_STUDENT");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.warnings[1].message")
                .asString()
                .contains("그린 월수반");
    }

    @Test
    void 요청_안_규칙과_데이터_오류가_요청_순서로_함께_온다() {
        MvcTestResult result = check("[{\"existingClassId\":" + greenId + ",\"students\":[{\"name\":\"김민준\"}]},"
                + "{\"existingClassId\":" + greenId + ",\"newClass\":" + newClass("새 반", "L9-B9", "[]")
                + ",\"students\":[{\"name\":\"김민준\"}]}]");

        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[*].field")
                .asArray()
                .containsExactly("classes[1]", "classes[1].students[0].name");
    }

    @Test
    void 형식_오류는_400() {
        MvcTestResult result =
                check("[{\"existingClassId\":" + greenId + ",\"students\":" + studentsJson("가", 51) + "}]");

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION");
    }

    @Test
    void 교사는_403() {
        MvcTestResult result = check(
                "[{\"existingClassId\":" + greenId + ",\"students\":[{\"name\":\"김민준\"}]}]",
                cookies.access(teacherId, UserRole.TEACHER, academyId),
                AuthCookieSupport.csrf());

        assertThat(result).hasStatus(403);
    }

    @Test
    void 비활성화된_관리자의_남은_access는_401() {
        jdbc.update("update `user` set status = 'inactive' where id = ?", adminId);
        entityManager.clear();

        assertThat(check("[{\"existingClassId\":" + greenId + ",\"students\":[{\"name\":\"김민준\"}]}]"))
                .hasStatus(401);
    }

    @Test
    void 사용권_시작_전이면_통과한다() {
        // 처음 시작하는 학원은 개강 준비 기간(before_start)에 명단을 넣는다 — 학습 허용(allowsLearning)과 헷갈리면 여기서 막힌다
        data.enforceLicenses(academyId);
        data.license(academyId, "L2-B1", TODAY.plusDays(1), TODAY.plusDays(60), 30);

        MvcTestResult result = check("[{\"existingClassId\":" + greenId + ",\"students\":" + studentsJson("가", 2) + "},"
                + "{\"newClass\":" + newClass("그린 화목반", "L2-B1", "[]") + ",\"students\":" + studentsJson("나", 2)
                + "}]");

        assertThat(result).bodyJson().extractingPath("$.errors").asArray().isEmpty();
    }

    @Test
    void 사용권_미적용_학원은_사용권_줄이_있어도_한도를_보지_않는다() {
        data.license(academyId, "L2-B1", TODAY.minusDays(9), TODAY.plusDays(60), 1);

        MvcTestResult result =
                check("[{\"existingClassId\":" + greenId + ",\"students\":" + studentsJson("가", 3) + "}]");

        assertThat(result).bodyJson().extractingPath("$.errors").asArray().isEmpty();
    }

    @Test
    void 한도는_처음_넘는_반_하나에만() {
        data.enforceLicenses(academyId);
        data.license(academyId, "L2-B1", TODAY.minusDays(9), TODAY.plusDays(60), 3);

        // 2 → 4(넘음) → 6: 두 번째 반 하나에만 오류
        MvcTestResult result = check("[{\"existingClassId\":" + greenId + ",\"students\":" + studentsJson("가", 2) + "},"
                + "{\"newClass\":" + newClass("그린 화목반", "L2-B1", "[]") + ",\"students\":" + studentsJson("나", 2)
                + "},"
                + "{\"newClass\":" + newClass("그린 금요반", "L2-B1", "[]") + ",\"students\":" + studentsJson("다", 2)
                + "}]");

        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[*].field")
                .asArray()
                .containsExactly("classes[1]");
    }

    @Test
    void 오류는_반_항목_순서로_정렬된다() {
        // 0번 반 고르기 오류(요청 규칙, 나중에 모음)가 1번 다른 학원 반 오류(데이터, 먼저 모음)보다 앞
        long other = data.academy("다른 학원");
        long otherClass = data.clazz(other, "L2-B1", "다른 반", TestData.uniqueAccessCode(), "active");

        MvcTestResult result = check("[{\"existingClassId\":" + greenId + ",\"newClass\":"
                + newClass("새 반", "L2-B1", "[]") + ",\"students\":[{\"name\":\"가\"}]},"
                + "{\"existingClassId\":" + otherClass + ",\"students\":[{\"name\":\"나\"}]}]");

        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[*].field")
                .asArray()
                .containsExactly("classes[0]", "classes[1].existingClassId");
    }

    @Test
    void 같은_이름_경고는_다른_학원_학생을_보지_않는다() throws Exception {
        long other = data.academy("다른 학원");
        long otherClass = data.clazz(other, "L2-B1", "다른 학원 비밀반", TestData.uniqueAccessCode(), "active");
        data.enroll(otherClass, data.student(other, "김민준"));

        MvcTestResult result = check("[{\"existingClassId\":" + greenId + ",\"students\":[{\"name\":\"김민준\"}]}]");

        assertThat(result).bodyJson().extractingPath("$.warnings").asArray().isEmpty();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("다른 학원 비밀반");
    }

    @Test
    void 요일과_반_이름_경계는_반_만들기와_같다() {
        String fifty = "가".repeat(50);
        String fiftyOne = "가".repeat(51);
        String item =
                "{\"newClass\":{\"name\":\"%s\",\"bookCode\":\"L2-B1\",\"startDate\":\"2026-12-01\",\"weekdays\":%s,"
                        + "\"teacherIds\":[]},\"students\":[{\"name\":\"학생%d\"}]}";

        MvcTestResult result = check("[" + String.format(item, "일요반", "[7]", 0) + ","
                + String.format(item, "영요일반", "[0]", 1) + ","
                + String.format(item, "팔요일반", "[8]", 2) + ","
                + String.format(item, fifty, "[1]", 3) + ","
                + String.format(item, fiftyOne, "[1]", 4) + "]");

        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[*].field")
                .asArray()
                .containsExactly(
                        "classes[1].newClass.weekdays", "classes[2].newClass.weekdays", "classes[4].newClass.name");
    }

    @Test
    void 반마다_학생_50명은_된다() {
        MvcTestResult result =
                check("[{\"existingClassId\":" + greenId + ",\"students\":" + studentsJson("가", 50) + "}]");

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.errors").asArray().isEmpty();
    }
}
