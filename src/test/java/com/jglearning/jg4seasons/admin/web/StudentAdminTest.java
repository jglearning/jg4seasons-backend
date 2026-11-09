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
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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
        Cookie refresh = refreshCookie(login(accessCode, code));

        assertCode(regenerate(classId, "[" + mine + "," + elsewhere + "]"), 404, "NOT_FOUND");

        assertThat(login(accessCode, code)).hasStatusOk();
        assertThat(mvc.post().uri("/api/v1/auth/refresh").cookie(refresh).exchange())
                .as("404면 정상 학생의 로그인 유지도 그대로")
                .hasStatusOk();
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
        long student = data.student(academyId, "김민준");
        data.enroll(classId, student);
        long otherClass = data.clazz(academyId, "L2-B1", "다른 반", TestData.uniqueAccessCode(), "active");
        jdbc.update("update `user` set status = 'inactive' where id = ?", adminId);

        assertThat(list(classId)).hasStatus(401);
        assertThat(register(classId, "[{\"name\":\"김민준\"}]")).hasStatus(401);
        assertThat(regenerate(classId, "[1]")).hasStatus(401);
        assertThat(patch(student, "{\"status\":\"inactive\"}")).hasStatus(401);
        assertThat(move(student, otherClass)).hasStatus(401);
        assertThat(search("김")).hasStatus(401);
        assertThat(jdbc.queryForObject("select status from `user` where id = ?", String.class, student))
                .isEqualTo("active");
    }

    @Test
    void 교사로_바뀐_관리자의_남은_access는_401() {
        long student = data.student(academyId, "김민준");
        data.enroll(classId, student);
        jdbc.update("update `user` set role = 'teacher' where id = ?", adminId);

        assertThat(patch(student, "{\"status\":\"inactive\"}")).hasStatus(401);
        assertThat(search("김")).hasStatus(401);
    }

    @Test
    void 여러_명_재발급하면_모두의_refresh가_끊긴다() throws Exception {
        MvcTestResult created = register(classId, "[{\"name\":\"가\"},{\"name\":\"나\"}]");
        int a = read(created, "$.created[0].id");
        int b = read(created, "$.created[1].id");
        Cookie refreshA = refreshCookie(login(accessCode, read(created, "$.created[0].personalCode")));
        Cookie refreshB = refreshCookie(login(accessCode, read(created, "$.created[1].personalCode")));

        assertThat(regenerate(classId, "[" + a + "," + b + "]")).hasStatusOk();

        assertThat(mvc.post().uri("/api/v1/auth/refresh").cookie(refreshA).exchange())
                .hasStatus(401);
        assertThat(mvc.post().uri("/api/v1/auth/refresh").cookie(refreshB).exchange())
                .hasStatus(401);
    }

    // ── 변경·퇴원·이동·검색 ──

    private MvcTestResult patch(long studentId, String body) {
        return mvc.patch()
                .uri("/api/v1/admin/students/" + studentId)
                .cookie(admin())
                .header(AuthCookieSupport.CSRF_HEADER, AuthCookieSupport.CSRF_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private MvcTestResult move(long studentId, long toClassId) {
        return post("/api/v1/admin/students/" + studentId + "/move", "{\"toClassId\":" + toClassId + "}");
    }

    private MvcTestResult search(String name) {
        return post("/api/v1/admin/students/search", "{\"name\":\"" + name + "\"}");
    }

    /** 반에 학생 한 명 등록 */
    private Registered registerOne(long inClass, String name) throws Exception {
        MvcTestResult created = register(inClass, "[{\"name\":\"" + name + "\"}]");
        assertThat(created).hasStatus(201);
        int id = read(created, "$.created[0].id");
        String code = read(created, "$.created[0].personalCode");
        return new Registered(id, code);
    }

    record Registered(long id, String code) {}

    private long auditCount(String action) {
        em.flush();
        return jdbc.queryForObject(
                "select count(*) from audit_log where academy_id = ? and action = ?", Long.class, academyId, action);
    }

    @Test
    void 퇴원생은_반_재발급에서_404() throws Exception {
        long id = registerOne(classId, "김민준").id();
        assertThat(patch(id, "{\"status\":\"inactive\"}")).hasStatusOk();

        assertCode(regenerate(classId, "[" + id + "]"), 404, "NOT_FOUND");
    }

    @Test
    void 퇴원하면_상태와_소속이_함께_저장된다() throws Exception {
        long id = registerOne(classId, "김민준").id();

        MvcTestResult result = patch(id, "{\"status\":\"inactive\"}");

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("inactive");
        em.flush();
        assertThat(jdbc.queryForObject("select status from `user` where id = ?", String.class, id))
                .isEqualTo("inactive");
        assertThat(jdbc.queryForObject(
                        "select count(*) from enrollment where student_id = ? and left_at is not null",
                        Integer.class,
                        id))
                .isEqualTo(1);
        assertThat((List<Object>) read(list(classId), "$.items")).isEmpty();
    }

    @Test
    void 퇴원하면_로그인이_401이고_refresh도_401() throws Exception {
        Registered student = registerOne(classId, "김민준");
        Cookie refresh = refreshCookie(login(accessCode, student.code()));

        assertThat(patch(student.id(), "{\"status\":\"inactive\"}")).hasStatusOk();

        assertCode(login(accessCode, student.code()), 401, "INVALID_CREDENTIALS");
        assertThat(mvc.post().uri("/api/v1/auth/refresh").cookie(refresh).exchange())
                .hasStatus(401);
    }

    @Test
    void 퇴원은_감사_로그_한_줄_다시_보내도_늘지_않는다() throws Exception {
        long id = registerOne(classId, "김민준").id();

        assertThat(patch(id, "{\"status\":\"inactive\"}")).hasStatusOk();
        assertThat(patch(id, "{\"status\":\"inactive\"}")).hasStatusOk();

        assertThat(auditCount("STUDENT_DEACTIVATE")).isEqualTo(1);
        String detail = jdbc.queryForObject(
                "select detail from audit_log where academy_id = ? and action = 'STUDENT_DEACTIVATE'",
                String.class,
                academyId);
        assertThat((List<Integer>) JsonPath.read(detail, "$.leftClassIds")).containsExactly((int) classId);
    }

    @Test
    void 되살리면_active지만_반은_없다() throws Exception {
        long id = registerOne(classId, "김민준").id();
        patch(id, "{\"status\":\"inactive\"}");

        MvcTestResult result = patch(id, "{\"status\":\"active\"}");

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("active");
        MvcTestResult found = search("김민준");
        assertThat((Object) read(found, "$.items[0].class")).isNull();
        assertThat((String) read(found, "$.items[0].status")).isEqualTo("active");
    }

    @Test
    void 되살린_학생은_예전_코드로_이동_후_입장된다() throws Exception {
        Registered student = registerOne(classId, "김민준");
        patch(student.id(), "{\"status\":\"inactive\"}");
        patch(student.id(), "{\"status\":\"active\"}");

        assertThat(move(student.id(), classId)).hasStatusOk();

        assertThat(login(accessCode, student.code())).hasStatusOk();
    }

    @Test
    void 학교를_빈_문자열로_보내면_null_이름_앞뒤_공백은_지운다() throws Exception {
        MvcTestResult created = register(classId, "[{\"name\":\"김민준\",\"school\":\"A중\",\"grade\":\"중1\"}]");
        int id = read(created, "$.created[0].id");

        MvcTestResult result = patch(id, "{\"name\":\" 김민수 \",\"school\":\"\"}");

        assertThat(result).hasStatusOk();
        Map<String, Object> body = read(result, "$");
        assertThat(body)
                .containsOnlyKeys("id", "name", "school", "grade", "status", "codeIssuedAt", "lastLoginAt")
                .containsEntry("name", "김민수")
                .containsEntry("school", null)
                .containsEntry("grade", "중1");
    }

    @Test
    void 이동하면_새_반_목록에_나오고_옛_반에서_빠지며_감사_로그() throws Exception {
        long id = registerOne(classId, "김민준").id();
        long toClass = data.clazz(academyId, "L2-B1", "B반", TestData.uniqueAccessCode(), "active");

        MvcTestResult result = move(id, toClass);

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.id").asNumber().isEqualTo((int) id);
        assertThat((List<Object>) read(list(classId), "$.items")).isEmpty();
        assertThat((List<Integer>) read(list(toClass), "$.items[*].id")).containsExactly((int) id);
        assertThat(auditCount("STUDENT_MOVE")).isEqualTo(1);
        String detail = jdbc.queryForObject(
                "select detail from audit_log where academy_id = ? and action = 'STUDENT_MOVE'",
                String.class,
                academyId);
        assertThat((List<Integer>) JsonPath.read(detail, "$.fromClassIds")).containsExactly((int) classId);
        assertThat((Integer) JsonPath.read(detail, "$.toClassId")).isEqualTo((int) toClass);
    }

    @Test
    void 이동_거절은_이유별_409() throws Exception {
        long id = registerOne(classId, "김민준").id();
        long finished = data.clazz(academyId, "L2-B1", "끝난 반", TestData.uniqueAccessCode(), "finished");
        long toClass = data.clazz(academyId, "L2-B1", "B반", TestData.uniqueAccessCode(), "active");

        assertCode(move(id, classId), 409, "ALREADY_IN_CLASS");
        assertCode(move(id, finished), 409, "CLASS_NOT_ACTIVE");
        patch(id, "{\"status\":\"inactive\"}");
        assertCode(move(id, toClass), 409, "STUDENT_INACTIVE");
        assertThat(auditCount("STUDENT_MOVE")).isZero();
    }

    @Test
    void 다른_학원_학생과_반은_404() throws Exception {
        long otherAcademy = data.academy("다른 학원");
        long otherStudent = data.student(otherAcademy, "남의 학생");
        long otherClass = data.clazz(otherAcademy, "L2-B1", "남의 반", TestData.uniqueAccessCode(), "active");
        long mine = registerOne(classId, "김민준").id();

        assertCode(patch(otherStudent, "{\"name\":\"바꿈\"}"), 404, "NOT_FOUND");
        assertCode(move(otherStudent, classId), 404, "NOT_FOUND");
        assertCode(move(mine, otherClass), 404, "NOT_FOUND");
        // 교사·관리자 id도 학생 경로로는 404
        assertCode(patch(adminId, "{\"name\":\"바꿈\"}"), 404, "NOT_FOUND");
    }

    @Test
    void 검색은_학원_전체_퇴원_포함_이름_순이고_JSON_키는_class() throws Exception {
        long b = registerOne(classId, "김민준").id();
        long a = registerOne(classId, "김가람").id();
        patch(b, "{\"status\":\"inactive\"}");
        data.student(data.academy("다른 학원"), "김다른");

        MvcTestResult found = search(" 김 ");

        assertThat(found).hasStatusOk();
        List<Map<String, Object>> items = read(found, "$.items");
        assertThat(items).extracting(m -> ((Number) m.get("id")).longValue()).containsExactly(a, b);
        assertThat(items.get(0)).containsOnlyKeys("id", "name", "school", "grade", "status", "class");
        assertThat((Map<String, Object>) items.get(0).get("class"))
                .containsOnlyKeys("id", "name")
                .containsEntry("name", "겨울 그린 A반");
        assertThat(items.get(1)).containsEntry("status", "inactive").containsEntry("class", null);
    }

    @Test
    void 검색어의_퍼센트와_밑줄은_글자_그대로() throws Exception {
        long percent = registerOne(classId, "김%").id();
        registerOne(classId, "김민");

        assertThat((List<Integer>) read(search("%"), "$.items[*].id")).containsExactly((int) percent);
        assertThat((List<Object>) read(search("김_"), "$.items")).isEmpty();
    }

    @Test
    void 검색은_최대_50명() throws Exception {
        String fifty = IntStream.range(0, 50)
                .mapToObj(i -> "{\"name\":\"박" + i + "\"}")
                .collect(Collectors.joining(",", "[", "]"));
        register(classId, fifty);
        register(classId, "[{\"name\":\"박50\"}]");

        assertThat((List<Object>) read(search("박"), "$.items")).hasSize(50);
    }
}
