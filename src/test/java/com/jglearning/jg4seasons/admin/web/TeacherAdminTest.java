package com.jglearning.jg4seasons.admin.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.auth.AuthCookieSupport;
import com.jglearning.jg4seasons.auth.UserRole;
import com.jglearning.jg4seasons.auth.token.RefreshOutcome;
import com.jglearning.jg4seasons.auth.token.RefreshTokenService;
import com.jglearning.jg4seasons.auth.token.SessionTtl;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

/** 5.3 /admin/teachers — 관리자가 교사 계정을 만들고·반을 배정하고·비활성화한다. 다른 학원 자원은 404. */
@IntegrationTest
@Transactional
class TeacherAdminTest {

    static final String URL = "/api/v1/admin/teachers";

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
    long classA;
    long classB;

    @BeforeEach
    void setUp() {
        data = new TestData(jdbc);
        academyId = data.academy("테스트 학원");
        adminId = data.staff(academyId, "관리자", "admin");
        classA = data.clazz(academyId, "L2-B1", "A반", TestData.uniqueAccessCode(), "active");
        classB = data.clazz(academyId, "L2-B1", "B반", TestData.uniqueAccessCode(), "active");
    }

    private Cookie[] adminCookies() {
        return new Cookie[] {cookies.access(adminId, UserRole.ADMIN, academyId), AuthCookieSupport.csrf()};
    }

    private MvcTestResult create(Cookie[] as, String body) {
        return mvc.post()
                .uri(URL)
                .cookie(as)
                .header(AuthCookieSupport.CSRF_HEADER, AuthCookieSupport.CSRF_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private MvcTestResult update(Cookie[] as, long id, String body) {
        return mvc.patch()
                .uri(URL + "/" + id)
                .cookie(as)
                .header(AuthCookieSupport.CSRF_HEADER, AuthCookieSupport.CSRF_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private String newTeacher(String loginId, long... classIds) {
        StringBuilder ids = new StringBuilder();
        for (long id : classIds) {
            ids.append(ids.isEmpty() ? "" : ",").append(id);
        }
        return "{\"name\":\"김교사\",\"loginId\":\"" + loginId
                + "\",\"password\":\"teacher-pass-1\",\"role\":\"teacher\",\"classIds\":[" + ids + "]}";
    }

    private long createdId(MvcTestResult result) {
        return ((Number) com.jayway.jsonpath.JsonPath.read(body(result), "$.id")).longValue();
    }

    private static String body(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString();
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void 교사를_만들면_201과_계정_정보() {
        MvcTestResult result = create(adminCookies(), newTeacher("New.Teacher", classB, classA));

        assertThat(result).hasStatus(201);
        assertThat(result).bodyJson().extractingPath("$.name").isEqualTo("김교사");
        assertThat(result).bodyJson().extractingPath("$.loginId").isEqualTo("new.teacher");
        assertThat(result).bodyJson().extractingPath("$.role").isEqualTo("teacher");
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("active");
        assertThat(result).bodyJson().extractingPath("$.classIds").asArray().containsExactly((int) classA, (int)
                classB);
        assertThat(result).bodyJson().extractingPath("$.lastLoginAt").isNull();
        assertThat(body(result)).doesNotContain("teacher-pass-1").doesNotContain("$2a$");
    }

    @Test
    void 목록에_교사와_담당_반이_나온다() {
        long teacherId = createdId(create(adminCookies(), newTeacher("list-me", classA)));

        MvcTestResult list = mvc.get().uri(URL).cookie(adminCookies()).exchange();

        assertThat(list).hasStatusOk();
        assertThat(list).bodyJson().extractingPath("$.total").asNumber().isEqualTo(2);
        assertThat(list).bodyJson().extractingPath("$.items[1].id").asNumber().isEqualTo((int) teacherId);
        assertThat(list)
                .bodyJson()
                .extractingPath("$.items[1].classIds")
                .asArray()
                .containsExactly((int) classA);
        assertThat(list).bodyJson().extractingPath("$.items[0].role").isEqualTo("admin");
    }

    @Test
    void 교사와_학생은_403() {
        long teacherId = data.staff(academyId, "교사", "teacher");
        long studentId = data.student(academyId, "학생");

        assertThat(mvc.get().uri(URL).cookie(cookies.access(teacherId, UserRole.TEACHER, academyId)))
                .hasStatus(403);
        assertThat(mvc.get().uri(URL).cookie(cookies.studentAccess(studentId, academyId)))
                .hasStatus(403);
    }

    @Test
    void X_CSRF가_없으면_403() {
        MvcTestResult result = mvc.post()
                .uri(URL)
                .cookie(adminCookies())
                .contentType(MediaType.APPLICATION_JSON)
                .content(newTeacher("no-csrf"))
                .exchange();

        assertThat(result).hasStatus(403);
    }

    @Test
    void 다른_학원_관리자는_교사를_바꾸지_못하고_404() {
        long teacherId = createdId(create(adminCookies(), newTeacher("mine-only")));
        long otherAcademy = data.academy("다른 학원");
        long otherAdmin = data.staff(otherAcademy, "다른 관리자", "admin");
        Cookie[] other = {cookies.access(otherAdmin, UserRole.ADMIN, otherAcademy), AuthCookieSupport.csrf()};

        MvcTestResult result = update(other, teacherId, "{\"name\":\"가로채기\"}");

        assertThat(result).hasStatus(404);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
    }

    @Test
    void 다른_학원_반을_배정하면_404() {
        long otherAcademy = data.academy("다른 학원");
        long otherClass = data.clazz(otherAcademy, "L2-B1", "다른 반", TestData.uniqueAccessCode(), "active");

        assertThat(create(adminCookies(), newTeacher("bad-class", otherClass))).hasStatus(404);
    }

    @Test
    void 이미_있는_아이디는_409() {
        create(adminCookies(), newTeacher("taken-id"));

        MvcTestResult again = create(adminCookies(), newTeacher("TAKEN-id"));

        assertThat(again).hasStatus(409);
        assertThat(again).bodyJson().extractingPath("$.code").isEqualTo("LOGIN_ID_TAKEN");
    }

    @Test
    void 비활성화된_관리자의_남은_access로는_교사_API를_못_쓴다() {
        // 관리자 B가 비활성화돼도 access는 최대 30분 유효하다 — 그 사이 자기를 되살리거나 새 관리자를 만들 수 없어야 한다
        long teacherId = createdId(create(adminCookies(), newTeacher("victim-teacher")));
        jdbc.update("update `user` set status = 'inactive' where id = ?", adminId);
        entityManager.clear(); // 같은 트랜잭션에서 앞서 읽은 관리자 엔티티가 남아 있지 않게(운영은 요청마다 새로 읽음)

        assertThat(mvc.get().uri(URL).cookie(adminCookies())).hasStatus(401);
        assertThat(create(adminCookies(), newTeacher("backdoor-admin"))).hasStatus(401);
        assertThat(update(adminCookies(), adminId, "{\"status\":\"active\"}"))
                .as("자기 되살리기")
                .hasStatus(401);
        assertThat(update(adminCookies(), teacherId, "{\"name\":\"x\"}")).hasStatus(401);
    }

    @Test
    void 정지된_학원의_관리자는_교사_API를_못_쓴다() {
        jdbc.update("update academy set status = 'suspended' where id = ?", academyId);

        MvcTestResult result = mvc.get().uri(URL).cookie(adminCookies()).exchange();

        assertThat(result).hasStatus(401);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("UNAUTHORIZED");
    }

    @Test
    void 관리자에서_교사로_내려간_사람의_남은_관리자_access도_못_쓴다() {
        jdbc.update("update `user` set role = 'teacher' where id = ?", adminId);

        assertThat(mvc.get().uri(URL).cookie(adminCookies())).hasStatus(401);
    }

    @Test
    void 자기_자신을_비활성화하면_409() {
        MvcTestResult result = update(adminCookies(), adminId, "{\"status\":\"inactive\"}");

        assertThat(result).hasStatus(409);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("SELF_CHANGE_NOT_ALLOWED");
    }

    @Test
    void 비활성화하면_refresh가_끊기고_me는_401() {
        long teacherId = createdId(create(adminCookies(), newTeacher("bye-teacher")));
        String refresh = refreshTokens.create(teacherId, null, SessionTtl.STAFF_REFRESH);
        Cookie teacherAccess = cookies.access(teacherId, UserRole.TEACHER, academyId);

        MvcTestResult result = update(adminCookies(), teacherId, "{\"status\":\"inactive\"}");

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("inactive");
        assertThat(refreshTokens.rotate(refresh)).isInstanceOf(RefreshOutcome.Rejected.class);
        assertThat(mvc.get().uri("/api/v1/me").cookie(teacherAccess))
                .as("남은 access도 바로")
                .hasStatus(401);
    }

    @Test
    void 담당_반과_이름을_바꾼다() {
        long teacherId = createdId(create(adminCookies(), newTeacher("move-classes", classA)));

        MvcTestResult result = update(adminCookies(), teacherId, "{\"name\":\"박교사\",\"classIds\":[" + classB + "]}");

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.name").isEqualTo("박교사");
        assertThat(result).bodyJson().extractingPath("$.classIds").asArray().containsExactly((int) classB);
    }

    @Test
    void 형식이_틀리면_400() {
        assertThat(
                        create(
                                adminCookies(),
                                "{\"name\":\"김교사\",\"loginId\":\"x-teacher\",\"password\":\"teacher-pass-1\",\"role\":\"superadmin\",\"classIds\":[]}"))
                .as("role은 teacher·admin만")
                .hasStatus(400);
        assertThat(
                        create(
                                adminCookies(),
                                "{\"name\":\"김교사\",\"loginId\":\"x-teacher\",\"password\":\"short\",\"role\":\"teacher\",\"classIds\":[]}"))
                .as("비밀번호 8자 미만")
                .hasStatus(400);
        assertThat(update(adminCookies(), adminId, "{\"status\":\"deleted\"}"))
                .as("status")
                .hasStatus(400);
    }
}
