package com.jglearning.jg4seasons.infra.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Spring Security가 막은 요청(MVC 전)도 같은 Problem Details 형식과 요청 ID로 응답한다. */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
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
    void 인증_없는_요청은_요청_ID와_함께_INFO로_남는다(CapturedOutput output) {
        MvcTestResult result = mvc.get().uri("/api/v1/student/home").exchange();

        String id = result.getResponse().getHeader("X-Request-Id");
        assertThat(output.getOut()).containsPattern("INFO \\[" + id + "\\].*UNAUTHORIZED");
    }

    @Test
    @WithMockUser
    void CSRF_거부는_WARN으로_남는다(CapturedOutput output) {
        MvcTestResult result = mvc.post()
                .uri("/test-support/errors/json")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"count\":5,\"flag\":true,\"name\":\"a\"}")
                .exchange();

        String id = result.getResponse().getHeader("X-Request-Id");
        assertThat(output.getOut()).containsPattern("WARN \\[" + id + "\\].*FORBIDDEN");
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

    @Test
    @WithMockUser
    void 컨트롤러_안의_권한_거부는_500이_아니라_FORBIDDEN이다(CapturedOutput output) {
        MvcTestResult result = mvc.get().uri("/test-support/errors/denied").exchange();

        assertThat(result).hasStatus(403).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("FORBIDDEN");
        assertThat(output.getOut())
                .doesNotContain("ERROR [" + result.getResponse().getHeader("X-Request-Id"));
    }

    @Test
    @WithMockUser
    void 컨트롤러_안의_인증_실패는_500이_아니라_UNAUTHORIZED다() {
        MvcTestResult result =
                mvc.get().uri("/test-support/errors/unauthenticated").exchange();

        assertThat(result).hasStatus(401).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("UNAUTHORIZED");
    }
}
