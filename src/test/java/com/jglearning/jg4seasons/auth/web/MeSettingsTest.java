package com.jglearning.jg4seasons.auth.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.auth.AuthCookieSupport;
import com.jglearning.jg4seasons.auth.UserRole;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

/** 1.8·1.9 학생 설정(ADR 0035): GET·PATCH /me/settings. 보낸 칸만 바꾸고 응답은 늘 설정 전체. */
@IntegrationTest
@Transactional
class MeSettingsTest {

    static final String URI = "/api/v1/me/settings";

    @Autowired
    MockMvcTester mvc;

    @Autowired
    AuthCookieSupport cookies;

    @Autowired
    JdbcTemplate jdbc;

    TestData data;
    long academyId;
    long studentId;

    @BeforeEach
    void setUp() {
        data = new TestData(jdbc);
        academyId = data.academy("테스트 학원");
        studentId = data.student(academyId, "김학생");
    }

    private MvcTestResult get(long userId) {
        return mvc.get()
                .uri(URI)
                .cookie(cookies.studentAccess(userId, academyId))
                .exchange();
    }

    private MvcTestResult patch(Cookie access, String body) {
        return mvc.patch()
                .uri(URI)
                .cookie(access, AuthCookieSupport.csrf())
                .header(AuthCookieSupport.CSRF_HEADER, AuthCookieSupport.CSRF_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private MvcTestResult patch(long userId, String body) {
        return patch(cookies.studentAccess(userId, academyId), body);
    }

    private int rows(long userId) {
        return jdbc.queryForObject("select count(*) from user_setting where user_id = ?", Integer.class, userId);
    }

    private String storedVoice(long userId) {
        return jdbc.queryForObject("select voice from user_setting where user_id = ?", String.class, userId);
    }

    @Test
    void 고른_적_없으면_voice는_null이고_조회는_행을_만들지_않는다() {
        MvcTestResult result = get(studentId);

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().isLenientlyEqualTo("{\"voice\": null}");
        assertThat(result).bodyJson().extractingPath("$").asMap().containsOnlyKeys("voice");
        assertThat(rows(studentId)).isZero();
    }

    @Test
    void 목소리를_저장하면_응답과_다음_조회에_나온다() {
        MvcTestResult patched = patch(studentId, "{\"voice\": \"uk_m\"}");

        assertThat(patched).hasStatusOk();
        assertThat(patched).bodyJson().extractingPath("$.voice").isEqualTo("uk_m");
        assertThat(patched).bodyJson().extractingPath("$").asMap().containsOnlyKeys("voice");
        assertThat(get(studentId)).bodyJson().extractingPath("$.voice").isEqualTo("uk_m");
        assertThat(storedVoice(studentId)).isEqualTo("uk_m");
    }

    @Test
    void null을_보내면_그_칸을_비운다() {
        patch(studentId, "{\"voice\": \"us_m\"}");

        MvcTestResult cleared = patch(studentId, "{\"voice\": null}");

        assertThat(cleared).hasStatusOk();
        assertThat(cleared).bodyJson().extractingPath("$.voice").isNull();
        assertThat(storedVoice(studentId)).isNull();
    }

    @Test
    void 보내지_않은_칸은_그대로다() {
        // 죽여야 하는 틀린 구현: 본문에 없는 칸을 null로 보고 지우는 것(전체 교체) — 옛 화면이 새 설정을 지운다
        patch(studentId, "{\"voice\": \"uk_w\"}");

        MvcTestResult empty = patch(studentId, "{}");

        assertThat(empty).hasStatusOk();
        assertThat(empty).bodyJson().extractingPath("$.voice").isEqualTo("uk_w");
        assertThat(storedVoice(studentId)).isEqualTo("uk_w");
    }

    @Test
    void 저장한_적_없는_학생의_빈_본문은_행을_만들지_않는다() {
        MvcTestResult empty = patch(studentId, "{}");

        assertThat(empty).hasStatusOk();
        assertThat(empty).bodyJson().extractingPath("$.voice").isNull();
        assertThat(rows(studentId)).isZero();
    }

    @Test
    void 같은_본문을_다시_보내도_결과가_같다() {
        patch(studentId, "{\"voice\": \"us_w\"}");

        MvcTestResult again = patch(studentId, "{\"voice\": \"us_w\"}");

        assertThat(again).hasStatusOk();
        assertThat(again).bodyJson().extractingPath("$.voice").isEqualTo("us_w");
        assertThat(rows(studentId)).isEqualTo(1);
    }

    @Test
    void 다른_학생의_설정은_보이지도_바뀌지도_않는다() {
        long classmateId = data.student(academyId, "이학생");
        patch(studentId, "{\"voice\": \"uk_m\"}");

        assertThat(get(classmateId)).bodyJson().extractingPath("$.voice").isNull();

        patch(classmateId, "{\"voice\": \"us_m\"}");
        assertThat(get(studentId)).bodyJson().extractingPath("$.voice").isEqualTo("uk_m");
    }

    @Test
    void 네_목소리_밖의_값은_400이고_저장되지_않는다() {
        patch(studentId, "{\"voice\": \"uk_w\"}");

        // 죽여야 하는 틀린 구현: 대소문자를 무시하거나 공백을 떼고 받는 것, DB 제약 오류를 500으로 내보내는 것
        for (String wrong : new String[] {"us_x", "US_W", " us_w", "us_w ", ""}) {
            MvcTestResult result = patch(studentId, "{\"voice\": \"" + wrong + "\"}");

            assertThat(result).as("[%s]", wrong).hasStatus(400);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION");
            assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("voice");
        }
        assertThat(storedVoice(studentId)).isEqualTo("uk_w");
    }

    @Test
    void 명세에_없는_필드와_타입이_다른_값은_400이다() {
        // 본문 없음·null·객체가 아닌 본문도 400이다(500이 아니다) — 서비스에 null 본문이 닿지 않는다
        for (String body : new String[] {
            "{\"voice\": \"us_w\", \"speed\": 1.25}",
            "{\"voice\": 1}",
            "{\"voice\": true}",
            "{\"voice\": {}}",
            "{\"voice\": [\"us_w\"]}",
            "",
            "null",
            "[]",
            "\"us_w\""
        }) {
            MvcTestResult result = patch(studentId, body);

            assertThat(result).as(body).hasStatus(400);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION");
        }
        assertThat(rows(studentId)).isZero();
    }

    @Test
    void 비활성_학생과_정지된_학원의_학생은_401이고_저장되지_않는다() {
        // 죽여야 하는 틀린 구현: 토큰만 믿고 저장하는 것 — 퇴원한 학생의 남은 access(30분)로 쓰인다
        jdbc.update("update `user` set status = 'inactive' where id = ?", studentId);
        assertThat(get(studentId)).as("비활성 조회").hasStatus(401);
        assertThat(patch(studentId, "{\"voice\": \"us_w\"}")).as("비활성 수정").hasStatus(401);

        jdbc.update("update `user` set status = 'active' where id = ?", studentId);
        jdbc.update("update academy set status = 'suspended' where id = ?", academyId);
        assertThat(get(studentId)).as("정지 학원 조회").hasStatus(401);
        assertThat(patch(studentId, "{\"voice\": \"us_w\"}")).as("정지 학원 수정").hasStatus(401);

        assertThat(rows(studentId)).isZero();
    }

    @Test
    void 다른_학원의_토큰으로는_401이다() {
        long otherAcademy = data.academy("다른 학원");
        Cookie forged = cookies.studentAccess(studentId, otherAcademy);

        assertThat(mvc.get().uri(URI).cookie(forged)).hasStatus(401);
        assertThat(patch(forged, "{\"voice\": \"us_w\"}")).hasStatus(401);
        assertThat(rows(studentId)).isZero();
    }

    @Test
    void 교사와_운영자는_403이고_쿠키가_없으면_401이다() {
        long teacherId = data.staff(academyId, "교사", "teacher");
        Cookie teacher = cookies.access(teacherId, UserRole.TEACHER, academyId);
        Cookie operator = cookies.operatorAccess(data.operator("운영자"));

        Cookie admin = cookies.access(data.staff(academyId, "관리자", "admin"), UserRole.ADMIN, academyId);

        for (Cookie staff : new Cookie[] {teacher, admin, operator}) {
            MvcTestResult read = mvc.get().uri(URI).cookie(staff).exchange();
            assertThat(read).hasStatus(403);
            assertThat(read).bodyJson().extractingPath("$.code").isEqualTo("FORBIDDEN");
            assertThat(patch(staff, "{\"voice\": \"us_w\"}")).hasStatus(403);
        }
        assertThat(mvc.get().uri(URI)).hasStatus(401);
        assertThat(rows(teacherId)).isZero();
    }

    @Test
    void 토큰은_학생인데_DB의_역할이_학생이_아니면_401이다() {
        // 죽여야 하는 틀린 구현: 역할을 토큰에서만 보는 것. 위의 403 테스트는 토큰 역할에서 끝나 서비스의 역할 확인에 닿지 않는다
        long teacherId = data.staff(academyId, "교사", "teacher");
        Cookie studentToken = cookies.studentAccess(teacherId, academyId);

        assertThat(mvc.get().uri(URI).cookie(studentToken)).hasStatus(401);
        assertThat(patch(studentToken, "{\"voice\": \"us_w\"}")).hasStatus(401);
        assertThat(rows(teacherId)).isZero();
    }

    @Test
    void X_CSRF_없이_수정하면_403이고_저장되지_않는다() {
        MvcTestResult result = mvc.patch()
                .uri(URI)
                .cookie(cookies.studentAccess(studentId, academyId), AuthCookieSupport.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"voice\": \"us_w\"}")
                .exchange();

        assertThat(result).hasStatus(403);
        assertThat(rows(studentId)).isZero();
    }
}
