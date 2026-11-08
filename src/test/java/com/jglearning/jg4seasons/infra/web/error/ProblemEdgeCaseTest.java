package com.jglearning.jg4seasons.infra.web.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.auth.AuthCookieSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** 리뷰에서 찾은 빈틈: 로그 없이 나가는 5xx, 칸 정보가 없는 400, 내부 문구 노출, 4xx 로그. */
@IntegrationTest
@WithMockUser
@ExtendWith(OutputCaptureExtension.class)
class ProblemEdgeCaseTest {

    private static final String BASE = "/test-support/errors";

    @Autowired
    MockMvcTester mvc;

    private static String requestId(MvcTestResult result) {
        return result.getResponse().getHeader("X-Request-Id");
    }

    private static void assertValidationField(MvcTestResult result, String field) {
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
    void 응답_변환_실패도_로그를_남기고_INTERNAL이다(CapturedOutput output) throws Exception {
        MvcTestResult result = mvc.get().uri(BASE + "/unwritable").exchange();

        assertThat(result).hasStatus(500).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INTERNAL");
        assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("잠시 후 다시 시도해 주세요");
        assertThat(result.getResponse().getContentAsString()).doesNotContain("unwritable-detail-xyz");
        assertThat(output.getOut()).contains("unwritable-detail-xyz").contains("[" + requestId(result) + "]");
    }

    @Test
    void 모듈_오류_code는_그_상태와_type과_시각_확장_필드로_나간다() {
        MvcTestResult result = mvc.get().uri(BASE + "/locked").exchange();

        assertThat(result).hasStatus(423).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("PROBE_LOCKED");
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo("/problems/probe-locked");
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo(423);
        assertThat(result).bodyJson().extractingPath("$.until").isEqualTo("2026-11-04T01:10:00Z");
        assertThat(result).bodyJson().extractingPath("$.requestId").isEqualTo(requestId(result));
    }

    @Test
    void 서버_오류_code의_ApiException은_원인과_함께_로그에_남고_응답에는_없다(CapturedOutput output) throws Exception {
        MvcTestResult result = mvc.get().uri(BASE + "/upstream").exchange();

        assertThat(result).hasStatus(502);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("PROBE_UPSTREAM");
        assertThat(result.getResponse().getContentAsString()).doesNotContain("cause-xyz");
        assertThat(output.getOut()).contains("cause-xyz").contains("[" + requestId(result) + "]");
    }

    @Test
    void 경로_변수_타입_오류는_칸_이름이다() {
        assertValidationField(
                mvc.get().uri(BASE + "/typed/abc?date=2026-11-04&page=1").exchange(), "id");
    }

    @Test
    void 쿼리_날짜_형식_오류는_칸_이름이다() {
        assertValidationField(
                mvc.get().uri(BASE + "/typed/1?date=2026-13-40&page=1").exchange(), "date");
    }

    @Test
    void 필수_쿼리_파라미터_누락은_칸_이름이다() {
        assertValidationField(mvc.get().uri(BASE + "/typed/1?page=1").exchange(), "date");
    }

    @Test
    void 쿼리_파라미터_제약_위반은_칸_이름이다() {
        assertValidationField(
                mvc.get().uri(BASE + "/typed/1?date=2026-11-04&page=0").exchange(), "page");
    }

    @Test
    void 경로_변수_제약이_있어도_본문_검증_오류는_본문_칸_이름이다() {
        // 메서드 검증으로 처리되면 칸 이름 대신 파라미터 이름(request)이 나가던 문제
        MvcTestResult result = mvc.post()
                .uri(BASE + "/typed/1/body")
                .cookie(AuthCookieSupport.csrf())
                .header(AuthCookieSupport.CSRF_HEADER, AuthCookieSupport.CSRF_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"a\",\"count\":0}")
                .exchange();

        assertValidationField(result, "count");
    }

    @Test
    void 경로_변수_제약_위반은_본문이_맞아도_칸_이름이다() {
        MvcTestResult result = mvc.post()
                .uri(BASE + "/typed/0/body")
                .cookie(AuthCookieSupport.csrf())
                .header(AuthCookieSupport.CSRF_HEADER, AuthCookieSupport.CSRF_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"a\",\"count\":1}")
                .exchange();

        assertValidationField(result, "id");
    }

    @Test
    void 올바른_쿼리는_통과한다() {
        assertThat(mvc.get().uri(BASE + "/typed/1?date=2026-11-04&page=1").exchange())
                .hasStatusOk();
    }

    @Test
    void 폼_바인딩_실패는_내부_문구를_숨긴다() throws Exception {
        MvcTestResult result = mvc.get().uri(BASE + "/model?count=abc").exchange();

        assertValidationField(result, "count");
        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("java.")
                .doesNotContain("Failed to convert")
                .doesNotContain("abc");
    }

    @Test
    void 객체_단위_검증_오류의_칸은_body다() {
        MvcTestResult result = mvc.post()
                .uri(BASE + "/consistent")
                .cookie(AuthCookieSupport.csrf())
                .header(AuthCookieSupport.CSRF_HEADER, AuthCookieSupport.CSRF_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"first\":\"a\",\"second\":\"b\"}")
                .exchange();

        assertValidationField(result, "body");
    }

    @Test
    void 입력_오류는_요청_ID와_함께_WARN으로_남고_입력값은_남지_않는다(CapturedOutput output) {
        MvcTestResult result = mvc.post()
                .uri(BASE + "/validation")
                .cookie(AuthCookieSupport.csrf())
                .header(AuthCookieSupport.CSRF_HEADER, AuthCookieSupport.CSRF_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"count\":-777}")
                .exchange();

        assertThat(result).hasStatus(400);
        String id = requestId(result);
        assertThat(output.getOut())
                .containsPattern("WARN \\[" + id + "\\].*VALIDATION")
                .doesNotContain("-777");
    }
}
