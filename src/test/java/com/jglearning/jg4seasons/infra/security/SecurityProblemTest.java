package com.jglearning.jg4seasons.infra.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Spring Security가 막은 요청(MVC 전)도 같은 Problem Details 형식과 요청 ID로 응답한다. */
@IntegrationTest
class SecurityProblemTest {

    @Autowired
    MockMvcTester mvc;

    @Test
    void 인증_없는_요청은_UNAUTHORIZED_문제_형식이다() {
        MvcTestResult result = mvc.get().uri("/api/v1/student/home").exchange();

        assertThat(result).hasStatus(401).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("UNAUTHORIZED");
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("로그인이 필요해요");
        assertThat(result).bodyJson().extractingPath("$.instance").isEqualTo("/api/v1/student/home");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.requestId")
                .isEqualTo(result.getResponse().getHeader("X-Request-Id"));
    }

    @Test
    @WithMockUser
    void CSRF_없는_상태_변경은_FORBIDDEN_문제_형식이다() {
        MvcTestResult result = mvc.post()
                .uri("/test-support/errors/json")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"count\":5,\"flag\":true,\"name\":\"a\"}")
                .exchange();

        assertThat(result).hasStatus(403).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("FORBIDDEN");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.requestId")
                .isEqualTo(result.getResponse().getHeader("X-Request-Id"));
    }
}
