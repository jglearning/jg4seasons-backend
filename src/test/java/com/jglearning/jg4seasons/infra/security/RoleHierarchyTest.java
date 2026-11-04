package com.jglearning.jg4seasons.infra.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.auth.AuthCookieSupport;
import com.jglearning.jg4seasons.auth.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** 역할 계층(03 §0.1): superadmin > admin > teacher, student는 별도. @RolesAllowed("TEACHER")는 admin도 통과. */
@IntegrationTest
class RoleHierarchyTest {

    static final String TEACHER_ONLY = "/test-support/security/teacher-only";
    static final String ADMIN_ONLY = "/test-support/security/admin-only";

    @Autowired
    MockMvcTester mvc;

    @Autowired
    AuthCookieSupport cookies;

    private int status(String uri, UserRole role) {
        return mvc.get()
                .uri(uri)
                .cookie(cookies.access(1L, role, 1L))
                .exchange()
                .getResponse()
                .getStatus();
    }

    @Test
    void 교사는_교사_전용을_부른다() {
        assertThat(status(TEACHER_ONLY, UserRole.TEACHER)).isEqualTo(200);
    }

    @Test
    void 관리자는_교사_전용도_부른다() {
        assertThat(status(TEACHER_ONLY, UserRole.ADMIN)).isEqualTo(200);
    }

    @Test
    void 시스템_관리자는_관리자_전용도_부른다() {
        assertThat(status(ADMIN_ONLY, UserRole.SUPERADMIN)).isEqualTo(200);
    }

    @Test
    void 교사는_관리자_전용을_못_부른다() {
        assertThat(status(ADMIN_ONLY, UserRole.TEACHER)).isEqualTo(403);
    }

    @Test
    void 학생은_교사_전용을_못_부른다() {
        assertThat(status(TEACHER_ONLY, UserRole.STUDENT)).isEqualTo(403);
    }
}
