package com.jglearning.jg4seasons.teacher.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.auth.AuthCookieSupport;
import com.jglearning.jg4seasons.auth.UserRole;
import com.jglearning.jg4seasons.auth.api.IssuedCode;
import com.jglearning.jg4seasons.auth.api.NewStudent;
import com.jglearning.jg4seasons.auth.api.StudentAccounts;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

/** 4.8 교사 개인 코드 재발급(T-04): 담당 반 학생만(관리자는 학원 안 소속 학생 전부 — 결정 K). 새 코드로 실제 입장까지 본다. */
@IntegrationTest
@Transactional
class TeacherPersonalCodeTest {

    /** 학생 로그인 잠금 카운터((반, IP), 인메모리)가 테스트끼리 섞이지 않게 요청마다 다른 IP */
    static final AtomicInteger IP_SEQ = new AtomicInteger(1);

    @Autowired
    MockMvcTester mvc;

    @Autowired
    AuthCookieSupport cookies;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    StudentAccounts students;

    @Autowired
    EntityManager em;

    TestData data;
    long academyId;
    long teacherId;
    long taughtClass;
    long otherClass;
    String taughtCode;
    String otherCode;

    @BeforeEach
    void setUp() {
        data = new TestData(jdbc);
        academyId = data.academy("교사 재발급 학원");
        teacherId = data.staff(academyId, "김교사", "teacher");
        taughtCode = TestData.uniqueAccessCode();
        otherCode = TestData.uniqueAccessCode();
        taughtClass = data.clazz(academyId, "L2-B1", "담당 반", taughtCode, "active");
        otherClass = data.clazz(academyId, "L2-B1", "남의 반", otherCode, "active");
        data.assignTeacher(taughtClass, teacherId);
    }

    /** 학생을 만들어 반에 넣는다. 처음 발급한 코드를 돌려준다 */
    private IssuedCode student(long academy, long classId, String name) {
        IssuedCode issued = students.create(academy, List.of(new NewStudent(name, null, null)))
                .get(0);
        em.flush();
        data.enroll(classId, issued.studentId());
        return issued;
    }

    private MvcTestResult regenerate(long actorId, UserRole role, long studentId) {
        return mvc.post()
                .uri("/api/v1/teacher/students/" + studentId + "/personal-code/regenerate")
                .cookie(cookies.access(actorId, role, academyId), AuthCookieSupport.csrf())
                .header(AuthCookieSupport.CSRF_HEADER, AuthCookieSupport.CSRF_VALUE)
                .exchange();
    }

    private MvcTestResult regenerate(long studentId) {
        return regenerate(teacherId, UserRole.TEACHER, studentId);
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

    private static <T> T read(MvcTestResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }

    private static void assertNotFound(MvcTestResult result) {
        assertThat(result).hasStatus(404);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
    }

    private int auditCount() {
        return jdbc.queryForObject("select count(*) from audit_log where academy_id = ?", Integer.class, academyId);
    }

    @Test
    void 담당_반_학생이면_새_코드로_입장되고_옛_코드는_401() throws Exception {
        IssuedCode before = student(academyId, taughtClass, "김민준");

        MvcTestResult result = regenerate(before.studentId());

        assertThat(result).hasStatusOk();
        String newCode = read(result, "$.personalCode");
        assertThat(newCode).matches("\\d{4}").isNotEqualTo(before.personalCode());
        assertThat(login(taughtCode, newCode)).hasStatusOk();
        assertThat(login(taughtCode, before.personalCode())).hasStatus(401);
    }

    @Test
    void 담당하지_않는_반_학생은_404이고_코드는_그대로() {
        IssuedCode other = student(academyId, otherClass, "이서연");

        assertNotFound(regenerate(other.studentId()));
        assertThat(login(otherCode, other.personalCode())).hasStatusOk();
        assertThat(auditCount()).isZero();
    }

    @Test
    void 다른_학원_학생은_404() {
        long otherAcademy = data.academy("다른 학원");
        long theirClass = data.clazz(otherAcademy, "L2-B1", "다른 학원 반", TestData.uniqueAccessCode(), "active");
        data.assignTeacher(theirClass, teacherId); // 데이터가 어긋나도 학원 경계가 먼저
        IssuedCode stranger = student(otherAcademy, theirClass, "남");

        assertNotFound(regenerate(stranger.studentId()));
    }

    @Test
    void 퇴원한_학생은_404() {
        IssuedCode left = student(academyId, taughtClass, "퇴원생");
        data.leave(taughtClass, left.studentId());
        jdbc.update("update `user` set status = 'inactive' where id = ?", left.studentId());

        assertNotFound(regenerate(left.studentId()));
    }

    @Test
    void 교사_id나_없는_id는_404() {
        long otherTeacher = data.staff(academyId, "다른 교사", "teacher");

        assertNotFound(regenerate(otherTeacher));
        assertNotFound(regenerate(Long.MAX_VALUE));
    }

    @Test
    void 관리자는_담당이_아니어도_학원_학생이면_재발급() throws Exception {
        long adminId = data.staff(academyId, "관리자", "admin");
        IssuedCode other = student(academyId, otherClass, "이서연");

        MvcTestResult result = regenerate(adminId, UserRole.ADMIN, other.studentId());

        assertThat(result).hasStatusOk();
        assertThat(login(otherCode, read(result, "$.personalCode"))).hasStatusOk();
    }

    @Test
    void 교사로_바뀐_관리자의_남은_ADMIN_access는_담당_반만() {
        // 범위는 쿠키의 역할이 아니라 DB의 지금 역할로 나눈다
        long demoted = data.staff(academyId, "강등된 관리자", "teacher");
        IssuedCode other = student(academyId, otherClass, "이서연");

        assertNotFound(regenerate(demoted, UserRole.ADMIN, other.studentId()));
        assertThat(login(otherCode, other.personalCode())).hasStatusOk();
    }

    @Test
    void 관리자도_반에_없는_학생은_404() {
        long adminId = data.staff(academyId, "관리자", "admin");
        long noClass = students.create(academyId, List.of(new NewStudent("반 없음", null, null)))
                .get(0)
                .studentId();

        assertNotFound(regenerate(adminId, UserRole.ADMIN, noClass));
    }

    @Test
    void 재발급하면_옛_refresh로_갱신이_401() {
        IssuedCode before = student(academyId, taughtClass, "김민준");
        Cookie refresh = Arrays.stream(
                        login(taughtCode, before.personalCode()).getResponse().getCookies())
                .filter(c -> c.getName().equals("refresh"))
                .findFirst()
                .orElseThrow();

        assertThat(regenerate(before.studentId())).hasStatusOk();

        assertThat(mvc.post().uri("/api/v1/auth/refresh").cookie(refresh).exchange())
                .hasStatus(401);
    }

    @Test
    void 감사_로그_한_줄() throws Exception {
        IssuedCode before = student(academyId, taughtClass, "김민준");

        MvcTestResult result = regenerate(before.studentId());
        String newCode = read(result, "$.personalCode");
        em.flush();

        List<Map<String, Object>> rows = jdbc.queryForList(
                "select actor_id, action, target_type, target_id, detail from audit_log where academy_id = ?",
                academyId);
        assertThat(rows).hasSize(1);
        Map<String, Object> row = rows.get(0);
        assertThat(((Number) row.get("actor_id")).longValue()).isEqualTo(teacherId);
        assertThat(row.get("action")).isEqualTo("PERSONAL_CODE_REGENERATE");
        assertThat(row.get("target_type")).isEqualTo("student");
        assertThat(((Number) row.get("target_id")).longValue()).isEqualTo(before.studentId());
        assertThat(JsonPath.<String>read(row.get("detail").toString(), "$.via")).isEqualTo("teacher");
        assertThat(row.get("detail").toString()).doesNotContain(newCode);
    }

    @Test
    void 응답_JSON_키() throws Exception {
        IssuedCode before = student(academyId, taughtClass, "김민준");

        MvcTestResult result = regenerate(before.studentId());

        Map<String, Object> body = read(result, "$");
        assertThat(body).containsOnlyKeys("studentId", "name", "personalCode");
        assertThat(((Number) body.get("studentId")).longValue()).isEqualTo(before.studentId());
        assertThat(body.get("name")).isEqualTo("김민준");
    }

    @Test
    void 비활성_교사는_401() {
        IssuedCode before = student(academyId, taughtClass, "김민준");
        jdbc.update("update `user` set status = 'inactive' where id = ?", teacherId);

        assertThat(regenerate(before.studentId())).hasStatus(401);
        assertThat(login(taughtCode, before.personalCode())).hasStatusOk();
    }
}
