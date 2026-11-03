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

    private void assertWideRejected(String json, String field) {
        MvcTestResult result = mvc.post()
                .uri("/test-support/errors/json-wide")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
                .exchange();
        assertThat(result).hasStatus(400).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION");
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo(field);
    }

    @Test
    void 숫자_래퍼_칸에_빈_문자열은_거부한다() {
        assertWideRejected("{\"id\":\"\"}", "id");
    }

    @Test
    void 숫자_래퍼_칸에_공백_문자열은_거부한다() {
        assertWideRejected("{\"id\":\"  \"}", "id");
    }

    @Test
    void 실수_칸에_빈_문자열은_거부한다() {
        assertWideRejected("{\"ratio\":\"\"}", "ratio");
    }

    @Test
    void 불리언_래퍼_칸에_빈_문자열은_거부한다() {
        assertWideRejected("{\"active\":\"\"}", "active");
    }

    @Test
    void 문자열_칸에_숫자는_거부한다() {
        assertWideRejected("{\"name\":123}", "name");
    }

    @Test
    void 문자열_칸에_불리언은_거부한다() {
        assertWideRejected("{\"name\":true}", "name");
    }

    @Test
    void enum_칸에_숫자는_거부한다() {
        assertWideRejected("{\"kind\":0}", "kind");
    }

    @Test
    void enum_칸에_없는_값은_거부한다() {
        assertWideRejected("{\"kind\":\"NOPE\"}", "kind");
    }

    @Test
    void 날짜_칸_형식_오류는_칸_이름이다() {
        assertWideRejected("{\"date\":\"2026-13-40\"}", "date");
    }

    @Test
    void 리스트_안_숫자_칸의_문자열은_인덱스_경로로_알린다() {
        assertWideRejected("{\"items\":[{\"count\":1},{\"count\":\"5\"}]}", "items[1].count");
    }

    @Test
    void 중첩_객체의_모르는_필드는_경로로_알린다() {
        assertWideRejected("{\"items\":[{\"count\":1,\"extra\":1}]}", "items[0].extra");
    }

    @Test
    void 중첩_검증_실패도_같은_경로_형식이다() {
        assertWideRejected("{\"items\":[{\"count\":0}]}", "items[0].count");
    }

    @Test
    void 원시_타입_칸이_빠지면_거부한다() {
        assertRejected("{\"flag\":true,\"name\":\"a\"}", "count");
    }

    @Test
    void 넓은_요청의_올바른_값과_빠진_선택_칸은_통과한다() {
        for (String json : new String[] {
            "{\"id\":1,\"active\":true,\"ratio\":0.5,\"kind\":\"A\",\"date\":\"2026-11-04\","
                    + "\"items\":[{\"count\":1}],\"name\":\"a\"}",
            "{}"
        }) {
            assertThat(mvc.post()
                            .uri("/test-support/errors/json-wide")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json)
                            .exchange())
                    .as(json)
                    .hasStatusOk();
        }
    }
}
