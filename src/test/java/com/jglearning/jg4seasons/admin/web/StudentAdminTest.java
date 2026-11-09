package com.jglearning.jg4seasons.admin.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.auth.AuthCookieSupport;
import com.jglearning.jg4seasons.auth.UserRole;
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

/** 5.2 학생 관리(A-02) 실제 동작: 등록·반 목록·개인 코드 재발급. 발급한 코드로 실제 입장까지 본다. */
@IntegrationTest
@Transactional
class StudentAdminTest {

    /** 학생 로그인 잠금 카운터((반, IP), 인메모리)가 테스트끼리 섞이지 않게 요청마다 다른 IP */
    static final AtomicInteger IP_SEQ = new AtomicInteger(1);

    @Autowired
    MockMvcTester mvc;

    @Autowired
    AuthCookieSupport cookies;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManager em;

    TestData data;
    long academyId;
    long adminId;
    long classId;
    String accessCode;

    @BeforeEach
    void setUp() {
        data = new TestData(jdbc);
        academyId = data.academy("학생 관리 학원");
        adminId = data.staff(academyId, "관리자", "admin");
        accessCode = TestData.uniqueAccessCode();
        classId = data.clazz(academyId, "L2-B1", "겨울 그린 A반", accessCode, "active");
    }

    private Cookie[] admin() {
        return new Cookie[] {cookies.access(adminId, UserRole.ADMIN, academyId), AuthCookieSupport.csrf()};
    }

    private MvcTestResult post(String path, String body) {
        return mvc.post()
                .uri(path)
                .cookie(admin())
                .header(AuthCookieSupport.CSRF_HEADER, AuthCookieSupport.CSRF_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private MvcTestResult register(long inClass, String studentsJson) {
        return post("/api/v1/admin/classes/" + inClass + "/students", "{\"students\":" + studentsJson + "}");
    }

    private MvcTestResult regenerate(long inClass, String idsJson) {
        return post(
                "/api/v1/admin/classes/" + inClass + "/students/personal-codes/regenerate",
                "{\"studentIds\":" + idsJson + "}");
    }

    private MvcTestResult list(long inClass) {
        return mvc.get()
                .uri("/api/v1/admin/classes/" + inClass + "/students")
                .cookie(admin())
                .exchange();
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

    private static String body(MvcTestResult result) throws Exception {
        return result.getResponse().getContentAsString();
    }

    private static <T> T read(MvcTestResult result, String path) throws Exception {
        return JsonPath.read(body(result), path);
    }

    private static Cookie refreshCookie(MvcTestResult login) {
        return Arrays.stream(login.getResponse().getCookies())
                .filter(c -> c.getName().equals("refresh"))
                .findFirst()
                .orElseThrow();
    }

    private static void assertCode(MvcTestResult result, int status, String code) {
        assertThat(result).hasStatus(status);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo(code);
    }

    @Test
    void 등록한_코드로_학생이_실제로_입장한다() throws Exception {
        MvcTestResult created = register(classId, "[{\"name\":\"김민준\",\"school\":\"A중\",\"grade\":\"중1\"}]");
        assertThat(created).hasStatus(201);
        int id = read(created, "$.created[0].id");
        String code = read(created, "$.created[0].personalCode");

        MvcTestResult login = login(accessCode, code);

        assertThat(login).hasStatusOk();
        assertThat(login).bodyJson().extractingPath("$.user.id").asNumber().isEqualTo(id);
        assertThat(login).bodyJson().extractingPath("$.class.id").asNumber().isEqualTo((int) classId);
    }

    @Test
    void 등록_응답은_요청_순서이고_JSON_키가_계약과_같다() throws Exception {
        MvcTestResult created = register(classId, "[{\"name\":\"최하늘\"},{\"name\":\" 김민준 \"},{\"name\":\"김민준\"}]");

        assertThat(created).hasStatus(201);
        List<Map<String, Object>> items = read(created, "$.created");
        assertThat(items).extracting(m -> m.get("name")).containsExactly("최하늘", "김민준", "김민준");
        assertThat(items.get(0)).containsOnlyKeys("id", "name", "personalCode");
        assertThat(items).extracting(m -> (String) m.get("personalCode")).allMatch(c -> c.matches("^\\d{4}$"));
        assertThat(items).extracting(m -> m.get("personalCode")).doesNotHaveDuplicates();
    }

    @Test
    void 반_목록은_현재_소속만_이름_순이고_평문_코드가_없다() throws Exception {
        MvcTestResult created = register(classId, "[{\"name\":\"최하늘\",\"school\":\"B중\"},{\"name\":\"김민준\"}]");
        List<String> codes = read(created, "$.created[*].personalCode");
        long otherClass = data.clazz(academyId, "L2-B1", "다른 반", TestData.uniqueAccessCode(), "active");
        long elsewhere = data.student(academyId, "다른 반 학생");
        data.enroll(otherClass, elsewhere);

        MvcTestResult listed = list(classId);

        assertThat(listed).hasStatusOk();
        List<Map<String, Object>> items = read(listed, "$.items");
        assertThat(items).extracting(m -> m.get("name")).containsExactly("김민준", "최하늘");
        assertThat(items.get(0))
                .containsOnlyKeys("id", "name", "school", "grade", "status", "codeIssuedAt", "lastLoginAt");
        assertThat(items.get(0)).containsEntry("status", "active").containsEntry("school", null);
        assertThat(items.get(0).get("codeIssuedAt")).isNotNull();
        assertThat(items.get(0).get("lastLoginAt")).isNull();
        assertThat(items.get(1)).containsEntry("school", "B중");
        assertThat(listed).bodyJson().extractingPath("$.total").asNumber().isEqualTo(2);
        for (String code : codes) {
            assertThat(body(listed)).doesNotContain("\"" + code + "\"");
        }
    }

    @Test
    void 로그인하면_목록에_마지막_로그인이_나온다() throws Exception {
        String code = read(register(classId, "[{\"name\":\"김민준\"}]"), "$.created[0].personalCode");
        assertThat(login(accessCode, code)).hasStatusOk();
        em.flush();

        assertThat((Object) read(list(classId), "$.items[0].lastLoginAt")).isNotNull();
    }

    @Test
    void 다른_학원_반은_404() {
        long otherAcademy = data.academy("다른 학원");
        long otherClass = data.clazz(otherAcademy, "L2-B1", "남의 반", TestData.uniqueAccessCode(), "active");
        long otherStudent = data.student(otherAcademy, "남의 학생");
        data.enroll(otherClass, otherStudent);

        assertCode(list(otherClass), 404, "NOT_FOUND");
        assertCode(register(otherClass, "[{\"name\":\"김민준\"}]"), 404, "NOT_FOUND");
        assertCode(regenerate(otherClass, "[" + otherStudent + "]"), 404, "NOT_FOUND");
    }

    @Test
    void 끝난_반에_등록하면_409_CLASS_NOT_ACTIVE() {
        long finished = data.clazz(academyId, "L2-B1", "끝난 반", TestData.uniqueAccessCode(), "finished");

        assertCode(register(finished, "[{\"name\":\"김민준\"}]"), 409, "CLASS_NOT_ACTIVE");
    }

    @Test
    void 재발급하면_옛_코드는_401이고_새_코드는_입장된다() throws Exception {
        MvcTestResult created = register(classId, "[{\"name\":\"김민준\"}]");
        int id = read(created, "$.created[0].id");
        String oldCode = read(created, "$.created[0].personalCode");

        MvcTestResult regenerated = regenerate(classId, "[" + id + "]");

        assertThat(regenerated).hasStatusOk();
        List<Map<String, Object>> items = read(regenerated, "$.items");
        assertThat(items).hasSize(1);
        assertThat(items.get(0)).containsOnlyKeys("id", "name", "personalCode").containsEntry("name", "김민준");
        String newCode = (String) items.get(0).get("personalCode");
        assertThat(newCode).matches("^\\d{4}$").isNotEqualTo(oldCode);
        assertCode(login(accessCode, oldCode), 401, "INVALID_CREDENTIALS");
        assertThat(login(accessCode, newCode)).hasStatusOk();
    }

    @Test
    void 재발급하면_옛_refresh로_갱신이_401() throws Exception {
        MvcTestResult created = register(classId, "[{\"name\":\"김민준\"}]");
        int id = read(created, "$.created[0].id");
        MvcTestResult login = login(accessCode, read(created, "$.created[0].personalCode"));
        Cookie refresh = refreshCookie(login);

        assertThat(regenerate(classId, "[" + id + "]")).hasStatusOk();

        assertThat(mvc.post().uri("/api/v1/auth/refresh").cookie(refresh).exchange())
                .hasStatus(401);
    }

    @Test
    void 재발급은_학생마다_감사_로그_한_줄() throws Exception {
        MvcTestResult created = register(classId, "[{\"name\":\"가\"},{\"name\":\"나\"}]");
        int a = read(created, "$.created[0].id");
        int b = read(created, "$.created[1].id");

        MvcTestResult regenerated = regenerate(classId, "[" + b + "," + a + "]");

        assertThat(regenerated).hasStatusOk();
        assertThat((List<Integer>) read(regenerated, "$.items[*].id")).containsExactly(b, a);
        em.flush();
        List<Map<String, Object>> logs = jdbc.queryForList(
                "select actor_id, action, target_type, target_id, detail from audit_log where academy_id = ?"
                        + " order by target_id",
                academyId);
        assertThat(logs).hasSize(2);
        assertThat(logs)
                .allSatisfy(row -> assertThat(row)
                        .containsEntry("actor_id", adminId)
                        .containsEntry("action", "PERSONAL_CODE_REGENERATE")
                        .containsEntry("target_type", "student"));
        assertThat(logs)
                .extracting(row -> ((Number) row.get("target_id")).intValue())
                .containsExactly(a, b);
        List<String> codes = read(regenerated, "$.items[*].personalCode");
        for (Map<String, Object> row : logs) {
            for (String code : codes) {
                assertThat(String.valueOf(row.get("detail"))).doesNotContain(code);
            }
        }
    }

    @Test
    void 다른_반_학생이_섞이면_404이고_아무도_바뀌지_않는다() throws Exception {
        MvcTestResult created = register(classId, "[{\"name\":\"김민준\"}]");
        int mine = read(created, "$.created[0].id");
        String code = read(created, "$.created[0].personalCode");
        long otherClass = data.clazz(academyId, "L2-B1", "다른 반", TestData.uniqueAccessCode(), "active");
        long elsewhere = data.student(academyId, "다른 반 학생");
        data.enroll(otherClass, elsewhere);

        assertCode(regenerate(classId, "[" + mine + "," + elsewhere + "]"), 404, "NOT_FOUND");

        assertThat(login(accessCode, code)).hasStatusOk();
        em.flush();
        assertThat(jdbc.queryForObject("select count(*) from audit_log where academy_id = ?", Integer.class, academyId))
                .isZero();
    }

    @Test
    void 같은_id를_두_번_보내면_한_번만_재발급() throws Exception {
        int id = read(register(classId, "[{\"name\":\"김민준\"}]"), "$.created[0].id");

        MvcTestResult regenerated = regenerate(classId, "[" + id + "," + id + "]");

        assertThat(regenerated).hasStatusOk();
        assertThat((List<Object>) read(regenerated, "$.items")).hasSize(1);
    }

    @Test
    void 비활성_관리자는_401() {
        jdbc.update("update `user` set status = 'inactive' where id = ?", adminId);

        assertThat(list(classId)).hasStatus(401);
        assertThat(register(classId, "[{\"name\":\"김민준\"}]")).hasStatus(401);
        assertThat(regenerate(classId, "[1]")).hasStatus(401);
    }
}
