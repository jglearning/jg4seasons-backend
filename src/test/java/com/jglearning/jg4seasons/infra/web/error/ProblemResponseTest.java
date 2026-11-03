package com.jglearning.jg4seasons.infra.web.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

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

/** 오류 응답은 Problem Details(application/problem+json) + code·requestId, 400 VALIDATION은 errors[]. API 명세 §0.4. */
@IntegrationTest
@WithMockUser
@ExtendWith(OutputCaptureExtension.class)
class ProblemResponseTest {

    private static final String BASE = "/test-support/errors";

    @Autowired
    MockMvcTester mvc;

    private MvcTestResult postValidation(String json) {
        return mvc.post()
                .uri(BASE + "/validation")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
                .exchange();
    }

    private static String requestIdHeader(MvcTestResult result) {
        return result.getResponse().getHeader("X-Request-Id");
    }

    @Test
    void 검증_실패는_VALIDATION과_칸별_오류다() {
        MvcTestResult result = postValidation("{\"name\":\"\",\"count\":0}");

        assertThat(result).hasStatus(400).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION");
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("입력을 확인해 주세요");
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo(400);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo("/problems/validation");
        assertThat(result).bodyJson().extractingPath("$.instance").isEqualTo(BASE + "/validation");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[*].field")
                .asArray()
                .contains("name", "count");
        assertThat(result).bodyJson().extractingPath("$.requestId").isEqualTo(requestIdHeader(result));
    }

    @Test
    void 검증_메시지는_한국어다() {
        MvcTestResult result = mvc.post()
                .uri(BASE + "/validation")
                .with(csrf())
                .header("Accept-Language", "en")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"count\":1}")
                .exchange();

        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[0].message")
                .asString()
                .matches(".*\\p{IsHangul}.*");
    }

    @Test
    void ApiException은_그_code와_확장_필드로_나간다() {
        MvcTestResult result = mvc.get().uri(BASE + "/api-exception").exchange();

        assertThat(result).hasStatus(404).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
        assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("회차를 찾을 수 없어요");
        assertThat(result).bodyJson().extractingPath("$.lessonCode").isEqualTo("L2-B1-99");
        assertThat(result).bodyJson().extractingPath("$.requestId").isEqualTo(requestIdHeader(result));
    }

    @Test
    void 없는_경로는_NOT_FOUND다() {
        MvcTestResult result = mvc.get().uri(BASE + "/nothing").exchange();

        assertThat(result).hasStatus(404).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
    }

    @Test
    void 지원하지_않는_메서드는_METHOD_NOT_ALLOWED다() {
        MvcTestResult result =
                mvc.delete().uri(BASE + "/validation").with(csrf()).exchange();

        assertThat(result).hasStatus(405);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("METHOD_NOT_ALLOWED");
    }

    @Test
    void 지원하지_않는_형식은_UNSUPPORTED_MEDIA_TYPE이다() {
        MvcTestResult result = mvc.post()
                .uri(BASE + "/validation")
                .with(csrf())
                .contentType(MediaType.TEXT_PLAIN)
                .content("name")
                .exchange();

        assertThat(result).hasStatus(415);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    void 서버_오류는_내부_메시지를_숨기고_로그에_남긴다(CapturedOutput output) throws Exception {
        MvcTestResult result = mvc.get().uri(BASE + "/boom").exchange();

        assertThat(result).hasStatus(500).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INTERNAL");
        assertThat(result.getResponse().getContentAsString()).doesNotContain("secret-detail-xyz");
        String requestId = requestIdHeader(result);
        assertThat(output.getOut()).contains("secret-detail-xyz").contains("[" + requestId + "]");
    }
}
