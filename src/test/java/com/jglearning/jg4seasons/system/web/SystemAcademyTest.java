package com.jglearning.jg4seasons.system.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.auth.AuthCookieSupport;
import com.jglearning.jg4seasons.auth.UserRole;
import jakarta.servlet.http.Cookie;
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

/**
 * 7.1·7.2 운영자 학원 목록·만들기(SYS-07, ADR 0031·0032). 학원 + 첫 관리자 + 감사 기록을 한 트랜잭션에서. 관리자 아이디가 겹칠 때의 롤백은
 * SystemAcademyCommitTest(테스트 트랜잭션 안에서는 롤백이 보이지 않는다).
 */
@IntegrationTest
@Transactional
class SystemAcademyTest {

    static final String URL = "/api/v1/system/academies";
    static final String ADMIN_PASSWORD = "partner-admin-1";
    static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    MockMvcTester mvc;

    @Autowired
    AuthCookieSupport cookies;

    @Autowired
    JdbcTemplate jdbc;

    TestData data;
    long operatorId;

    @BeforeEach
    void setUp() {
        data = new TestData(jdbc);
        operatorId = data.operator("운영자");
    }

    private static String unique(String prefix) {
        return prefix + SEQ.incrementAndGet() + "-" + System.nanoTime() % 100_000;
    }

    private MvcTestResult create(Cookie access, String body) {
        return mvc.post()
                .uri(URL)
                .cookie(access, AuthCookieSupport.csrf())
                .header(AuthCookieSupport.CSRF_HEADER, AuthCookieSupport.CSRF_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private MvcTestResult create(String body) {
        return create(cookies.operatorAccess(operatorId), body);
    }

    private static String body(String slug, String loginId, String extra) {
        return "{\"name\":\"연계 학원\",\"slug\":\"" + slug + "\",\"licenseEnforced\":true" + extra
                + ",\"admin\":{\"loginId\":\"" + loginId + "\",\"password\":\"" + ADMIN_PASSWORD
                + "\",\"name\":\"원 관리자\"}}";
    }

    private Integer count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private static void assertValidation(MvcTestResult result, String field) {
        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION");
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo(field);
    }

    @Test
    void 학원과_첫_관리자를_만들고_관리자는_바로_로그인한다() {
        String slug = unique("partner");
        String loginId = unique("padmin");

        MvcTestResult result = create(body(slug, loginId, ""));

        assertThat(result).hasStatus(201);
        assertThat(result).bodyJson().extractingPath("$.slug").isEqualTo(slug);
        assertThat(result).bodyJson().extractingPath("$.timezone").isEqualTo("Asia/Seoul");
        assertThat(result).bodyJson().extractingPath("$.licenseEnforced").isEqualTo(true);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("active");
        assertThat(result).bodyJson().extractingPath("$.admin.loginId").isEqualTo(loginId);
        Map<String, Object> academy = jdbc.queryForMap(
                "select id, name, license_enforced, settings->>'$.timezone' as timezone from academy where slug = ?",
                slug);
        assertThat(academy)
                .containsEntry("name", "연계 학원")
                .containsEntry("license_enforced", true)
                .containsEntry("timezone", "Asia/Seoul");
        long academyId = ((Number) academy.get("id")).longValue();

        MvcTestResult login = mvc.post()
                .uri("/api/v1/auth/staff/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + ADMIN_PASSWORD + "\"}")
                .with(request -> {
                    request.setRemoteAddr("198.18.3." + (SEQ.get() % 250 + 1));
                    return request;
                })
                .exchange();
        assertThat(login).hasStatusOk();
        assertThat(login).bodyJson().extractingPath("$.user.role").isEqualTo("admin");
        assertThat(login)
                .bodyJson()
                .extractingPath("$.user.academyId")
                .asNumber()
                .isEqualTo((int) academyId);
    }

    @Test
    void 감사_기록을_남기고_비밀번호는_없다() {
        String slug = unique("audit");

        assertThat(create(body(slug, unique("padmin"), ""))).hasStatus(201);

        Map<String, Object> audit = jdbc.queryForMap(
                "select l.actor_id, l.target_type, l.detail from audit_log l join academy a on a.id = l.academy_id"
                        + " where a.slug = ? and l.action = 'ACADEMY_CREATE'",
                slug);
        assertThat(((Number) audit.get("actor_id")).longValue()).isEqualTo(operatorId);
        assertThat(audit.get("target_type")).isEqualTo("academy");
        assertThat(String.valueOf(audit.get("detail"))).contains(slug).doesNotContain(ADMIN_PASSWORD);
    }

    @Test
    void 타임존을_정하면_그대로_저장한다() {
        String slug = unique("tz");

        MvcTestResult result = create(body(slug, unique("padmin"), ",\"timezone\":\"America/New_York\""));

        assertThat(result).bodyJson().extractingPath("$.timezone").isEqualTo("America/New_York");
        assertThat(jdbc.queryForObject(
                        "select settings->>'$.timezone' from academy where slug = ?", String.class, slug))
                .isEqualTo("America/New_York");
    }

    @Test
    void slug가_있으면_409이고_아무것도_남지_않는다() {
        String slug = unique("dup");
        assertThat(create(body(slug, unique("padmin"), ""))).hasStatus(201);
        int users = count("select count(*) from `user`");
        int audits = count("select count(*) from audit_log");
        String loginId = unique("padmin");

        MvcTestResult again = create(body(slug, loginId, ""));

        assertThat(again).hasStatus(409);
        assertThat(again).bodyJson().extractingPath("$.code").isEqualTo("ACADEMY_SLUG_TAKEN");
        assertThat(count("select count(*) from `user`")).isEqualTo(users);
        assertThat(count("select count(*) from audit_log")).isEqualTo(audits);
    }

    @Test
    void licenseEnforced가_없으면_400() {
        String withoutFlag = body(unique("noflag"), unique("padmin"), "").replace(",\"licenseEnforced\":true", "");

        assertValidation(create(withoutFlag), "licenseEnforced");
    }

    @Test
    void 타임존이_모르는_값이거나_오프셋이면_400() {
        assertValidation(create(body(unique("tz"), unique("padmin"), ",\"timezone\":\"Mars/Base\"")), "timezone");
        assertValidation(create(body(unique("tz"), unique("padmin"), ",\"timezone\":\"+09:00\"")), "timezone");
    }

    @Test
    void slug_형식이_틀리거나_demo면_400() {
        assertValidation(create(body("Partner_A", unique("padmin"), "")), "slug");
        assertValidation(create(body("demo", unique("padmin"), "")), "slug");
    }

    @Test
    void 관리자_비밀번호가_72바이트를_넘으면_admin_password로_400() {
        // 한글 25자 = 25자(DTO 글자 수 통과)·75바이트 — 이 요청에서 그 칸의 경로는 admin.password(리뷰 #47-5)
        String body = "{\"name\":\"연계 학원\",\"slug\":\"" + unique("bytes") + "\",\"licenseEnforced\":true,"
                + "\"admin\":{\"loginId\":\"" + unique("padmin") + "\",\"password\":\"" + "가".repeat(25)
                + "\",\"name\":\"관리자\"}}";

        assertValidation(create(body), "admin.password");
    }

    @Test
    void 모르는_필드는_400() {
        assertThat(create(body(unique("x"), unique("padmin"), ",\"seats\":10"))).hasStatus(400);
    }

    @Test
    void 운영자가_아니면_403() {
        long academyId = data.academy("관리 학원");
        long adminId = data.staff(academyId, "관리자", "admin");
        long teacherId = data.staff(academyId, "교사", "teacher");
        String body = body(unique("x"), unique("padmin"), "");

        assertThat(create(cookies.access(adminId, UserRole.ADMIN, academyId), body))
                .hasStatus(403);
        assertThat(create(cookies.access(teacherId, UserRole.TEACHER, academyId), body))
                .hasStatus(403);
        assertThat(mvc.get().uri(URL).cookie(cookies.access(adminId, UserRole.ADMIN, academyId)))
                .hasStatus(403);
    }

    @Test
    void 비활성_운영자는_401() {
        jdbc.update("update `user` set status = 'inactive' where id = ?", operatorId);

        assertThat(create(body(unique("x"), unique("padmin"), ""))).hasStatus(401);
        assertThat(mvc.get().uri(URL).cookie(cookies.operatorAccess(operatorId)))
                .hasStatus(401);
    }

    @Test
    void X_CSRF가_없으면_403() {
        MvcTestResult result = mvc.post()
                .uri(URL)
                .cookie(cookies.operatorAccess(operatorId), AuthCookieSupport.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(unique("x"), unique("padmin"), ""))
                .exchange();

        assertThat(result).hasStatus(403);
    }

    @Test
    void 목록은_id_순이고_만든_학원이_있다() throws Exception {
        String slug = unique("list");
        assertThat(create(body(slug, unique("padmin"), ""))).hasStatus(201);

        MvcTestResult result =
                mvc.get().uri(URL).cookie(cookies.operatorAccess(operatorId)).exchange();

        assertThat(result).hasStatusOk();
        List<Integer> ids = com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8), "$.items[*].id");
        assertThat(ids).isSorted();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.items[?(@.slug == '" + slug + "')].licenseEnforced")
                .isEqualTo(List.of(true));
    }
}
