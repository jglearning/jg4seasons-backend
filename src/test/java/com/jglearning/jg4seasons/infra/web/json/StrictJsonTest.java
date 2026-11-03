package com.jglearning.jg4seasons.infra.web.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import com.jglearning.jg4seasons.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** 요청 JSON은 명세 타입 그대로만 받는다(CLAUDE.md §9.4). 본문 JsonProbe(int count, boolean flag, String name). */
@IntegrationTest
@WithMockUser
class StrictJsonTest {

    @Autowired
    MockMvcTester mvc;

    private MvcTestResult post(String json) {
        return mvc.post()
                .uri("/test-support/errors/json")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
                .exchange();
    }

    private void assertRejected(String json, String field) {
        MvcTestResult result = post(json);
        assertThat(result).hasStatus(400).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION");
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo(field);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[0].message")
                .asString()
                .matches(".*\\p{IsHangul}.*");
    }

    @Test
    void 숫자_칸에_문자열은_거부한다() {
        assertRejected("{\"count\":\"5\",\"flag\":true,\"name\":\"a\"}", "count");
    }

    @Test
    void 정수_칸에_실수는_거부한다() {
        assertRejected("{\"count\":5.5,\"flag\":true,\"name\":\"a\"}", "count");
    }

    @Test
    void 불리언_칸에_문자열은_거부한다() {
        assertRejected("{\"count\":5,\"flag\":\"true\",\"name\":\"a\"}", "flag");
    }

    @Test
    void 명세에_없는_필드는_거부한다() {
        assertRejected("{\"count\":5,\"flag\":true,\"name\":\"a\",\"extra\":1}", "extra");
    }

    @Test
    void 깨진_JSON은_거부한다() {
        assertRejected("{\"count\":", "body");
    }

    @Test
    void JSON_뒤의_쓰레기_값은_거부한다() {
        assertRejected("{\"count\":5,\"flag\":true,\"name\":\"a\"}x", "body");
    }

    @Test
    void 올바른_요청은_통과한다() {
        assertThat(post("{\"count\":5,\"flag\":true,\"name\":\"a\"}")).hasStatusOk();
    }
}
