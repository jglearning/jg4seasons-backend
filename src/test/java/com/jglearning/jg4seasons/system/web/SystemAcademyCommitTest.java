package com.jglearning.jg4seasons.system.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.auth.AuthCookieSupport;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * 실제로 커밋되는 7.2 학원 만들기의 롤백(리뷰에서 볼 곳 3). 학원을 넣은 뒤 관리자 아이디가 겹쳐 409가 나면 학원·감사 기록이 남지 않아야 한다 — 테스트
 * 트랜잭션 안에서는 롤백이 보이지 않아 커밋되는 테스트로 본다. 넣은 행은 끝에 지운다.
 */
@IntegrationTest
class SystemAcademyCommitTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    AuthCookieSupport cookies;

    @Autowired
    JdbcTemplate jdbc;

    long operatorId;
    String suffix;

    @BeforeEach
    void setUp() {
        operatorId = new TestData(jdbc).operator("커밋 운영자");
        suffix = String.valueOf(System.nanoTime() % 1_000_000);
    }

    @AfterEach
    void cleanUp() {
        List<Long> academyIds =
                jdbc.queryForList("select id from academy where slug like ?", Long.class, "commit-%-" + suffix);
        for (long academyId : academyIds) {
            jdbc.update("delete from audit_log where academy_id = ?", academyId);
            jdbc.update(
                    "delete from auth_identity where user_id in (select id from `user` where academy_id = ?)",
                    academyId);
            jdbc.update("delete from `user` where academy_id = ?", academyId);
            jdbc.update("delete from academy where id = ?", academyId);
        }
        jdbc.update("delete from `user` where id = ?", operatorId);
    }

    private MvcTestResult create(String slug, String loginId) {
        return mvc.post()
                .uri("/api/v1/system/academies")
                .cookie(cookies.operatorAccess(operatorId), AuthCookieSupport.csrf())
                .header(AuthCookieSupport.CSRF_HEADER, AuthCookieSupport.CSRF_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"커밋 학원\",\"slug\":\"" + slug + "\",\"licenseEnforced\":true,"
                        + "\"admin\":{\"loginId\":\"" + loginId
                        + "\",\"password\":\"commit-admin-1\",\"name\":\"관리자\"}}")
                .exchange();
    }

    private Integer count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    @Test
    void 관리자_아이디가_겹치면_학원도_감사_기록도_남지_않는다() {
        String loginId = "commit-admin-" + suffix;
        assertThat(create("commit-a-" + suffix, loginId)).hasStatus(201);
        int audits = count("select count(*) from audit_log");

        MvcTestResult result = create("commit-b-" + suffix, loginId);

        assertThat(result).hasStatus(409);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("LOGIN_ID_TAKEN");
        assertThat(count("select count(*) from academy where slug = ?", "commit-b-" + suffix))
                .as("학원 롤백")
                .isZero();
        assertThat(count("select count(*) from audit_log")).as("감사 기록 롤백").isEqualTo(audits);
    }
}
