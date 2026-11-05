package com.jglearning.jg4seasons.admin.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.auth.AuthCookieSupport;
import com.jglearning.jg4seasons.auth.UserRole;
import com.jglearning.jg4seasons.auth.token.RefreshOutcome;
import com.jglearning.jg4seasons.auth.token.RefreshTokenService;
import com.jglearning.jg4seasons.auth.token.SessionTtl;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import java.io.UnsupportedEncodingException;
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

/** 5.1 /admin/classes — 반 생성(진도표 24행 + 가입 코드)·조회·수정·가입 코드 재발급. 다른 학원 자원은 404. */
@IntegrationTest
@Transactional
class ClassAdminTest {

    static final String URL = "/api/v1/admin/classes";
    /** 가입 코드 확인의 IP 한도는 컨텍스트에 남는다 → 요청마다 다른 IP */
    static final AtomicInteger IP_SEQ = new AtomicInteger(1);

    @Autowired
    MockMvcTester mvc;

    @Autowired
    AuthCookieSupport cookies;

    @Autowired
    RefreshTokenService refreshTokens;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManager entityManager;

    TestData data;
    long academyId;
    long adminId;
    long teacherId;
    long otherAcademy;

    @BeforeEach
    void setUp() {
        data = new TestData(jdbc);
        academyId = data.academy("반 관리 학원");
        adminId = data.staff(academyId, "관리자", "admin");
        teacherId = data.staff(academyId, "김교사", "teacher");
        otherAcademy = data.academy("다른 학원");
    }

    private Cookie[] adminCookies() {
        return new Cookie[] {cookies.access(adminId, UserRole.ADMIN, academyId), AuthCookieSupport.csrf()};
    }

    private MvcTestResult post(Cookie[] as, String path, String body) {
        return mvc.post()
                .uri(path)
                .cookie(as)
                .header(AuthCookieSupport.CSRF_HEADER, AuthCookieSupport.CSRF_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private MvcTestResult patch(Cookie[] as, long id, String body) {
        return mvc.patch()
                .uri(URL + "/" + id)
                .cookie(as)
                .header(AuthCookieSupport.CSRF_HEADER, AuthCookieSupport.CSRF_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private MvcTestResult regenerate(Cookie[] as, long id) {
        return mvc.post()
                .uri(URL + "/" + id + "/access-code/regenerate")
                .cookie(as)
                .header(AuthCookieSupport.CSRF_HEADER, AuthCookieSupport.CSRF_VALUE)
                .exchange();
    }

    private static String newClass(String name, String bookCode, String weekdays, String teacherIds) {
        return "{\"name\":\"" + name + "\",\"bookCode\":\"" + bookCode + "\",\"startDate\":\"2026-11-09\","
                + "\"weekdays\":" + weekdays + ",\"teacherIds\":" + teacherIds + "}";
    }

    private long createClass(String name) {
        MvcTestResult result = post(adminCookies(), URL, newClass(name, "L2-B1", "[1,3]", "[" + teacherId + "]"));
        assertThat(result).hasStatus(201);
        return ((Number) JsonPath.read(body(result), "$.id")).longValue();
    }

    private String accessCodeOf(long classId) {
        entityManager.flush();
        return jdbc.queryForObject("select access_code from `class` where id = ?", String.class, classId);
    }

    private int verifyCode(String code) {
        String ip = "198.51.100." + IP_SEQ.getAndIncrement();
        return mvc.post()
                .uri("/api/v1/auth/code/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\"}")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .exchange()
                .getResponse()
                .getStatus();
    }

    private int classCount(long academy) {
        entityManager.flush();
        return jdbc.queryForObject("select count(*) from `class` where academy_id = ?", Integer.class, academy);
    }

    private static String body(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString();
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void 반을_만들면_201과_진도표_24행() {
        MvcTestResult result =
                post(adminCookies(), URL, newClass(" 겨울 그린 A반 ", "L2-B1", "[3,1]", "[" + teacherId + "]"));

        assertThat(result).hasStatus(201);
        long id = ((Number) JsonPath.read(body(result), "$.id")).longValue();
        assertThat(result).bodyJson().extractingPath("$.name").isEqualTo("겨울 그린 A반");
        assertThat(result).bodyJson().extractingPath("$.bookCode").isEqualTo("L2-B1");
        assertThat(result).bodyJson().extractingPath("$.levelCode").isEqualTo("L2");
        assertThat(result).bodyJson().extractingPath("$.season").isEqualTo("겨울");
        assertThat(result).bodyJson().extractingPath("$.startDate").isEqualTo("2026-11-09");
        assertThat(result).bodyJson().extractingPath("$.weekdays").asArray().containsExactly(1, 3);
        assertThat(result).bodyJson().extractingPath("$.accessCode").asString().matches("[A-Z2-9]{8}");
        assertThat(result).bodyJson().extractingPath("$.codeIssuedAt").isNotNull();
        assertThat(result).bodyJson().extractingPath("$.allowFutureLessons").isEqualTo(false);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("active");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.teachers[0].id")
                .asNumber()
                .isEqualTo((int) teacherId);
        assertThat(result).bodyJson().extractingPath("$.teachers[0].name").isEqualTo("김교사");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.studentCount")
                .asNumber()
                .isEqualTo(0);

        List<Map<String, Object>> sessions = jdbc.queryForList(
                "select s.seq, l.number, s.scheduled_date from class_session s join lesson l on l.id = s.lesson_id"
                        + " where s.class_id = ? order by s.seq",
                id);
        assertThat(sessions).hasSize(24);
        assertThat(sessions.get(0).get("number")).isEqualTo(1);
        assertThat(sessions.get(23).get("number")).isEqualTo(24);
        assertThat(sessions.get(0).get("scheduled_date").toString()).isEqualTo("2026-11-09");
        assertThat(sessions.get(23).get("scheduled_date").toString()).isEqualTo("2027-01-27");
    }

    @Test
    void 교사는_403() {
        Cookie[] teacher = {cookies.access(teacherId, UserRole.TEACHER, academyId), AuthCookieSupport.csrf()};

        assertThat(mvc.get().uri(URL).cookie(teacher).exchange()).hasStatus(403);
        assertThat(post(teacher, URL, newClass("A반", "L2-B1", "[1]", "[]"))).hasStatus(403);
    }

    @Test
    void X_CSRF가_없으면_403() {
        MvcTestResult result = mvc.post()
                .uri(URL)
                .cookie(adminCookies())
                .contentType(MediaType.APPLICATION_JSON)
                .content(newClass("A반", "L2-B1", "[1]", "[]"))
                .exchange();

        assertThat(result).hasStatus(403);
        assertThat(classCount(academyId)).isZero();
    }

    @Test
    void 없는_권은_400_bookCode() {
        MvcTestResult result = post(adminCookies(), URL, newClass("A반", "L9-B9", "[1]", "[]"));

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION");
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("bookCode");
    }

    @Test
    void 요일이_잘못되면_400_weekdays() {
        for (String weekdays : List.of("[]", "[0]", "[8]", "[1,1]")) {
            MvcTestResult result = post(adminCookies(), URL, newClass("A반", "L2-B1", weekdays, "[]"));

            assertThat(result).as(weekdays).hasStatus(400);
            assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("weekdays");
        }
        assertThat(classCount(academyId)).isZero();
    }

    @Test
    void 이름이_비면_400_name() {
        MvcTestResult result = post(adminCookies(), URL, newClass("   ", "L2-B1", "[1]", "[]"));

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("name");
    }

    @Test
    void 다른_학원_교사_학생_비활성_교사는_404이고_반이_안_생긴다() {
        long otherTeacher = data.staff(otherAcademy, "남의 교사", "teacher");
        long student = data.student(academyId, "학생");
        long inactive = data.staff(academyId, "쉬는 교사", "teacher");
        jdbc.update("update `user` set status = 'inactive' where id = ?", inactive);

        for (long wrong : new long[] {otherTeacher, student, inactive, -1L}) {
            MvcTestResult result = post(adminCookies(), URL, newClass("A반", "L2-B1", "[1]", "[" + wrong + "]"));
            assertThat(result).as("teacherId %d", wrong).hasStatus(404);
        }
        assertThat(classCount(academyId)).isZero();
    }

    @Test
    void 다른_학원_반은_GET_PATCH_재발급_모두_404() {
        long otherAdmin = data.staff(otherAcademy, "남의 관리자", "admin");
        Cookie[] other = {cookies.access(otherAdmin, UserRole.ADMIN, otherAcademy), AuthCookieSupport.csrf()};
        long classId = createClass("A반");
        String code = accessCodeOf(classId);

        assertThat(mvc.get().uri(URL + "/" + classId).cookie(other).exchange()).hasStatus(404);
        assertThat(patch(other, classId, "{\"name\":\"뺏기\"}")).hasStatus(404);
        assertThat(regenerate(other, classId)).hasStatus(404);
        assertThat(mvc.get().uri(URL).cookie(other).exchange())
                .bodyJson()
                .extractingPath("$.total")
                .asNumber()
                .isEqualTo(0);
        assertThat(accessCodeOf(classId)).isEqualTo(code);
    }

    @Test
    void PATCH의_teacherIds에_다른_학원_교사가_있으면_404이고_그대로() {
        long classId = createClass("A반");
        long otherTeacher = data.staff(otherAcademy, "남의 교사", "teacher");

        assertThat(patch(adminCookies(), classId, "{\"teacherIds\":[" + otherTeacher + "]}"))
                .hasStatus(404);
        assertThat(mvc.get().uri(URL + "/" + classId).cookie(adminCookies()).exchange())
                .bodyJson()
                .extractingPath("$.teachers[0].id")
                .asNumber()
                .isEqualTo((int) teacherId);
    }

    @Test
    void 목록은_status로_거르고_없으면_전부() {
        long first = createClass("A반");
        long second = createClass("B반");
        assertThat(patch(adminCookies(), second, "{\"status\":\"finished\"}")).hasStatusOk();

        assertThat(mvc.get().uri(URL).cookie(adminCookies()).exchange())
                .bodyJson()
                .extractingPath("$.items[*].id")
                .asArray()
                .containsExactly((int) first, (int) second);
        assertThat(mvc.get().uri(URL + "?status=active").cookie(adminCookies()).exchange())
                .bodyJson()
                .extractingPath("$.items[*].id")
                .asArray()
                .containsExactly((int) first);
        assertThat(mvc.get().uri(URL + "?status=closed").cookie(adminCookies()).exchange())
                .hasStatus(400);
    }

    @Test
    void PATCH는_빠진_칸을_그대로_두고_teacherIds로_교체한다() {
        long classId = createClass("A반");
        long second = data.staff(academyId, "박교사", "teacher");

        MvcTestResult result =
                patch(adminCookies(), classId, "{\"allowFutureLessons\":true,\"teacherIds\":[" + second + "]}");

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.name").isEqualTo("A반");
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("active");
        assertThat(result).bodyJson().extractingPath("$.allowFutureLessons").isEqualTo(true);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.teachers[*].id")
                .asArray()
                .containsExactly((int) second);
        assertThat(patch(adminCookies(), classId, "{\"bookCode\":\"L2-B2\"}"))
                .as("권은 바꿀 수 없음 — 모르는 필드")
                .hasStatus(400);
        MvcTestResult badStatus = patch(adminCookies(), classId, "{\"status\":\"closed\"}");
        assertThat(badStatus).hasStatus(400);
        assertThat(badStatus).bodyJson().extractingPath("$.errors[0].field").isEqualTo("status");
    }

    @Test
    void 상태를_finished로_바꾸면_그_코드로_입장_못_하고_active면_다시_된다() {
        long classId = createClass("A반");
        String code = accessCodeOf(classId);
        assertThat(verifyCode(code)).isEqualTo(200);

        MvcTestResult finished = patch(adminCookies(), classId, "{\"status\":\"finished\"}");
        assertThat(finished).hasStatusOk();
        assertThat(finished)
                .bodyJson()
                .extractingPath("$.teachers[*].id")
                .asArray()
                .as("teacherIds를 빼면 담당 교사는 그대로")
                .containsExactly((int) teacherId);
        entityManager.flush();
        assertThat(verifyCode(code)).isEqualTo(404);

        assertThat(patch(adminCookies(), classId, "{\"status\":\"active\"}")).hasStatusOk();
        entityManager.flush();
        assertThat(verifyCode(code)).isEqualTo(200);
    }

    @Test
    void 재발급하면_옛_코드는_막히고_새_코드는_되고_기존_로그인은_유지되고_감사_1행() {
        long classId = createClass("A반");
        String oldCode = accessCodeOf(classId);
        long student = data.student(academyId, "학생");
        data.enroll(classId, student);
        String refresh = refreshTokens.create(student, null, SessionTtl.STUDENT_REFRESH);

        MvcTestResult result = regenerate(adminCookies(), classId);

        assertThat(result).hasStatusOk();
        String newCode = JsonPath.read(body(result), "$.accessCode");
        assertThat(newCode).isNotEqualTo(oldCode).matches("[A-Z2-9]{8}");
        assertThat(result).bodyJson().extractingPath("$.codeIssuedAt").isNotNull();
        entityManager.flush();
        assertThat(verifyCode(oldCode)).isEqualTo(404);
        assertThat(verifyCode(newCode)).isEqualTo(200);
        assertThat(refreshTokens.rotate(refresh)).isInstanceOf(RefreshOutcome.Rotated.class);

        Map<String, Object> audit = jdbc.queryForMap(
                "select actor_id, action, target_type, target_id, detail from audit_log" + " where academy_id = ?",
                academyId);
        assertThat(audit)
                .containsEntry("actor_id", adminId)
                .containsEntry("action", "CODE_REGENERATE")
                .containsEntry("target_type", "class")
                .containsEntry("target_id", classId)
                .containsEntry("detail", null);
    }

    @Test
    void 비활성화된_관리자의_남은_access는_401() {
        long classId = createClass("A반");
        jdbc.update("update `user` set status = 'inactive' where id = ?", adminId);
        entityManager.clear();

        assertThat(mvc.get().uri(URL).cookie(adminCookies()).exchange()).hasStatus(401);
        assertThat(regenerate(adminCookies(), classId)).hasStatus(401);
        assertThat(post(adminCookies(), URL, newClass("B반", "L2-B1", "[1]", "[]")))
                .hasStatus(401);
    }

    @Test
    void 비활성_교사가_담당으로_남은_반도_조회되고_같은_목록으로_PATCH할_수_있다() {
        long classId = createClass("A반");
        long newcomer = data.staff(academyId, "새 교사", "teacher");
        jdbc.update("update `user` set status = 'inactive' where id in (?, ?)", teacherId, newcomer);
        entityManager.clear();

        MvcTestResult list = mvc.get().uri(URL).cookie(adminCookies()).exchange();
        assertThat(list).hasStatusOk();
        assertThat(list)
                .bodyJson()
                .extractingPath("$.items[0].teachers[0].name")
                .isEqualTo("김교사");
        MvcTestResult detail =
                mvc.get().uri(URL + "/" + classId).cookie(adminCookies()).exchange();
        assertThat(detail).hasStatusOk();
        assertThat(detail)
                .bodyJson()
                .extractingPath("$.teachers[0].id")
                .asNumber()
                .isEqualTo((int) teacherId);

        MvcTestResult kept = patch(adminCookies(), classId, "{\"name\":\"B반\",\"teacherIds\":[" + teacherId + "]}");
        assertThat(kept).as("이미 배정된 비활성 교사는 그대로 둘 수 있음").hasStatusOk();
        assertThat(kept).bodyJson().extractingPath("$.name").isEqualTo("B반");

        assertThat(patch(adminCookies(), classId, "{\"teacherIds\":[" + teacherId + "," + newcomer + "]}"))
                .as("새로 넣는 비활성 교사는 404")
                .hasStatus(404);
    }
}
