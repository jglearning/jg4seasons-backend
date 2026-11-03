package com.jglearning.jg4seasons.infra.web.requestid;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** 요청 ID: 서버가 요청마다 새로 만들어 응답 헤더 X-Request-Id로 돌려준다. */
@IntegrationTest
class RequestIdIntegrationTest {

    private static final String HEALTH = "/actuator/health";

    @Autowired
    MockMvcTester mvc;

    private String requestId() {
        return mvc.get().uri(HEALTH).exchange().getResponse().getHeader("X-Request-Id");
    }

    @Test
    void 응답마다_16자리_요청_ID가_붙는다() {
        assertThat(requestId()).matches("^[0-9a-f]{16}$");
    }

    @Test
    void 요청마다_다른_ID다() {
        assertThat(requestId()).isNotEqualTo(requestId());
    }

    @Test
    void 클라이언트가_보낸_요청_ID는_쓰지_않는다() {
        String id = mvc.get()
                .uri(HEALTH)
                .header("X-Request-Id", "injected")
                .exchange()
                .getResponse()
                .getHeader("X-Request-Id");
        assertThat(id).isNotEqualTo("injected").matches("^[0-9a-f]{16}$");
    }
}
