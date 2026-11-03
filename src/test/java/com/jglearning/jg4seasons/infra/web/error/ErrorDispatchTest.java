package com.jglearning.jg4seasons.infra.web.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * MVC 밖에서 난 오류(방화벽 거부·필터 예외)는 서블릿 ERROR 디스패치로 /error에 온다. MockMvc는 ERROR 디스패치를 하지 않아 /error를 오류 속성과
 * 함께 직접 부른다. 실제 경로(`/a;b` 등)는 로컬 서버에서 확인한다.
 */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class ErrorDispatchTest {

    @Autowired
    MockMvcTester mvc;

    @Test
    void 오류_디스패치는_로그인_없이도_원래_상태의_문제_형식이다() {
        MvcTestResult result = mvc.get()
                .uri("/error")
                .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 400)
                .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/api/v1/a;b")
                .exchange();

        assertThat(result).hasStatus(400).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION");
        assertThat(result).bodyJson().extractingPath("$.instance").isEqualTo("/api/v1/a;b");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.requestId")
                .isEqualTo(result.getResponse().getHeader("X-Request-Id"));
    }

    @Test
    void 오류_디스패치의_500은_고정_문구로_응답하고_원인은_로그에_남긴다(CapturedOutput output) throws Exception {
        MvcTestResult result = mvc.get()
                .uri("/error")
                .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 500)
                .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/api/v1/x")
                .requestAttr(RequestDispatcher.ERROR_EXCEPTION, new IllegalStateException("dispatch-xyz"))
                .exchange();

        assertThat(result).hasStatus(500);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INTERNAL");
        assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("잠시 후 다시 시도해 주세요");
        assertThat(result.getResponse().getContentAsString()).doesNotContain("dispatch-xyz");
        // 로그의 경로는 /error가 아니라 원래 요청 경로
        assertThat(output.getOut()).contains("dispatch-xyz").contains("GET /api/v1/x");
    }

    @Test
    void 오류_정보_없이_직접_부르면_NOT_FOUND다() {
        MvcTestResult result = mvc.get().uri("/error").exchange();

        assertThat(result).hasStatus(404);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
    }
}
