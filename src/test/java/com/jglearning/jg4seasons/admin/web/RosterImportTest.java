package com.jglearning.jg4seasons.admin.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.MutableClock;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.auth.AuthCookieSupport;
import com.jglearning.jg4seasons.auth.UserRole;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
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
 * 5.5 명단 가져오기 만들기(ADR 0036): 새 반·기존 반에 새 학생을 만들고 반별 가입 코드·개인 코드를 준다. 오늘 = 서울 2026-11-10. 실패 때 아무것도 남지 않는지는
 * 커밋이 필요해 RosterImportCommitTest.
 */
@IntegrationTest
@Transactional
class RosterImportTest {

    static final Instant NOON_KST = Instant.parse("2026-11-10T03:00:00Z");
    static final AtomicInteger IP_SEQ = new AtomicInteger(1);

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
    long teacherId;
    long greenId;
    String greenCode;

    @BeforeEach
    void setUp() {
        clock.set(NOON_KST);
        data = new TestData(jdbc);
        academyId = data.academy("가져오기 만들기 학원");
        adminId = data.staff(academyId, "관리자", "admin");
        teacherId = data.staff(academyId, "교사", "teacher");
        greenCode = TestData.uniqueAccessCode();
        greenId = data.clazz(academyId, "L2-B1", "그린 월수반", greenCode, "active");
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    private MvcTestResult create(String classesJson) {
        return mvc.post()
                .uri("/api/v1/admin/roster-imports")
                .cookie(cookies.access(adminId, UserRole.ADMIN, academyId), AuthCookieSupport.csrf())
                .header(AuthCookieSupport.CSRF_HEADER, AuthCookieSupport.CSRF_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"classes\":" + classesJson + "}")
                .exchange();
    }

    private String newClass(String name, String bookCode, String teacherIds) {
        return "{\"name\":\"" + name + "\",\"bookCode\":\"" + bookCode
                + "\",\"startDate\":\"2026-12-01\",\"weekdays\":[2,4],\"teacherIds\":" + teacherIds + "}";
    }

    private MvcTestResult login(String code, String personalCode) {
        String ip = "198.51.100." + IP_SEQ.getAndIncrement();
        return mvc.post()
                .uri("/api/v1/auth/student/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\",\"personalCode\":\"" + personalCode + "\"}")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .exchange();
    }

    private <T> T read(MvcTestResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }

    private long count(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }

    @Test
    void 새_반과_기존_반에_학생을_만들고_반별_코드를_준다() throws Exception {
        MvcTestResult result = create("[{\"newClass\":" + newClass("블루 화목반", "L3-B1", "[" + teacherId + "]")
                + ",\"students\":[{\"name\":\"이서연\",\"school\":\"A중\",\"grade\":\"중1\"},{\"name\":\"박지호\"}]},"
                + "{\"existingClassId\":" + greenId + ",\"students\":[{\"name\":\"김민준\"}]}]");

        assertThat(result).hasStatus(201);
        assertThat(result).bodyJson().extractingPath("$.createdClasses").isEqualTo(1);
        assertThat(result).bodyJson().extractingPath("$.createdStudents").isEqualTo(3);

        long blueId = ((Number) read(result, "$.classes[0].classId")).longValue();
        assertThat(result).bodyJson().extractingPath("$.classes[0].created").isEqualTo(true);
        assertThat(result).bodyJson().extractingPath("$.classes[0].name").isEqualTo("블루 화목반");
        assertThat(result).bodyJson().extractingPath("$.classes[0].levelCode").isEqualTo("L3");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.classes[0].level.nameKo")
                .isEqualTo("블루");
        assertThat((String) read(result, "$.classes[0].accessCode")).hasSize(8);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.classes[0].students[*].name")
                .asArray()
                .containsExactly("이서연", "박지호");
        List<String> codes = read(result, "$.classes[*].students[*].personalCode");
        assertThat(codes).hasSize(3).allMatch(c -> c.matches("^\\d{4}$")).doesNotHaveDuplicates();

        assertThat(result).bodyJson().extractingPath("$.classes[1].classId").isEqualTo((int) greenId);
        assertThat(result).bodyJson().extractingPath("$.classes[1].created").isEqualTo(false);
        assertThat(result).bodyJson().extractingPath("$.classes[1].accessCode").isEqualTo(greenCode);

        assertThat(count("select count(*) from class_session where class_id = ?", blueId))
                .isEqualTo(24);
        assertThat(count("select count(*) from class_teacher where class_id = ? and user_id = ?", blueId, teacherId))
                .isEqualTo(1);
        assertThat(count("select count(*) from enrollment where class_id = ? and left_at is null", blueId))
                .isEqualTo(2);
        assertThat(count("select count(*) from enrollment where class_id = ? and left_at is null", greenId))
                .isEqualTo(1);
        assertThat(count(
                        "select count(*) from `user` where academy_id = ? and role = 'student' and name = '이서연'"
                                + " and school = 'A중' and grade = '중1'",
                        academyId))
                .isEqualTo(1);
    }

    @Test
    void 받은_개인_코드로_입장할_수_있다() throws Exception {
        MvcTestResult result = create("[{\"existingClassId\":" + greenId + ",\"students\":[{\"name\":\"김민준\"}]}]");

        String code = read(result, "$.classes[0].students[0].personalCode");
        assertThat(login(greenCode, code)).hasStatusOk();
    }

    @Test
    void 경고만_있으면_만든다() {
        data.enroll(greenId, data.student(academyId, "김민준"));

        MvcTestResult result =
                create("[{\"newClass\":" + newClass("그린 월수반", "L2-B1", "[]") + ",\"students\":[{\"name\":\"김민준\"}]}]");

        assertThat(result).hasStatus(201);
        assertThat(count("select count(*) from `user` where academy_id = ? and name = '김민준'", academyId))
                .isEqualTo(2);
    }

    @Test
    void 요청_안_오류는_400_VALIDATION과_위치() throws Exception {
        MvcTestResult result = create("[{\"existingClassId\":" + greenId + ",\"students\":[{\"name\":\"김민준\"}]},"
                + "{\"newClass\":" + newClass("새 반", "L2-B1", "[]") + ",\"students\":[{\"name\":\"김민준\"}]}]");

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION");
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("classes[1].students[0].name");
        // Problem Details의 detail·errors[].message에 입력값(이름)을 싣지 않는다(§9.4) — 이름이 든 문구는 검사 응답에만
        assertThat(result).bodyJson().extractingPath("$.errors[0].message").isEqualTo("명단을 다시 검사해 주세요");
        assertThat(result.getResponse().getContentAsString()).doesNotContain("김민준");
    }

    @Test
    void 다른_학원_반이면_404() {
        long other = data.academy("다른 학원");
        long otherClass = data.clazz(other, "L2-B1", "다른 반", TestData.uniqueAccessCode(), "active");

        MvcTestResult result = create("[{\"existingClassId\":" + otherClass + ",\"students\":[{\"name\":\"김민준\"}]}]");

        assertThat(result).hasStatus(404);
        assertThat(count("select count(*) from enrollment where class_id = ?", otherClass))
                .isZero();
    }

    @Test
    void 끝난_반이면_409_CLASS_NOT_ACTIVE() {
        long finished = data.clazz(academyId, "L2-B1", "지난 반", TestData.uniqueAccessCode(), "finished");

        MvcTestResult result = create("[{\"existingClassId\":" + finished + ",\"students\":[{\"name\":\"김민준\"}]}]");

        assertThat(result).hasStatus(409);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("CLASS_NOT_ACTIVE");
    }

    @Test
    void 감사_로그는_한_줄이고_이름과_코드가_없다() throws Exception {
        MvcTestResult result = create("[{\"newClass\":" + newClass("블루 화목반", "L3-B1", "[]")
                + ",\"students\":[{\"name\":\"이서연\"}]},"
                + "{\"existingClassId\":" + greenId + ",\"students\":[{\"name\":\"김민준\"},{\"name\":\"박지호\"}]}]");
        long blueId = ((Number) read(result, "$.classes[0].classId")).longValue();
        List<String> codes = read(result, "$.classes[*].students[*].personalCode");

        List<String> details = jdbc.queryForList(
                "select detail from audit_log where academy_id = ? and action = 'ROSTER_IMPORT'"
                        + " and target_type = 'ACADEMY' and target_id = ? and actor_id = ?",
                String.class,
                academyId,
                academyId,
                adminId);

        assertThat(details).singleElement().satisfies(detail -> {
            assertThat(JsonPath.<List<Integer>>read(detail, "$.createdClassIds"))
                    .containsExactly((int) blueId);
            assertThat(JsonPath.<List<Integer>>read(detail, "$.existingClassIds"))
                    .containsExactly((int) greenId);
            assertThat(JsonPath.<Integer>read(detail, "$.createdStudents")).isEqualTo(3);
            assertThat(detail).doesNotContain("이서연", "김민준", "박지호", "personalCode");
            codes.forEach(code -> assertThat(detail).doesNotContain("\"" + code + "\""));
        });
    }

    @Test
    void 관리자가_아니면_403() {
        MvcTestResult result = mvc.post()
                .uri("/api/v1/admin/roster-imports")
                .cookie(cookies.access(teacherId, UserRole.TEACHER, academyId), AuthCookieSupport.csrf())
                .header(AuthCookieSupport.CSRF_HEADER, AuthCookieSupport.CSRF_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"classes\":[{\"existingClassId\":" + greenId + ",\"students\":[{\"name\":\"김민준\"}]}]}")
                .exchange();

        assertThat(result).hasStatus(403);
    }
}
