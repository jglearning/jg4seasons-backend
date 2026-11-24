package com.jglearning.jg4seasons.wordtest.reading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.anthropic.models.beta.AnthropicBeta;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.MessageCreateParams;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 답안지 읽기 요청 만들기·응답 해석(네트워크 없음). 프롬프트에는 문항 수만 들어가고 정답은 들어가지 않는다 */
class ReadRequestsTest {

    static final String TEMPLATE = "번호 1~{{itemCount}}의 손글씨를 옮겨 적어라";
    static final byte[] IMAGE = {1, 2, 3};

    @Test
    void 요청에_모델_토큰_effort_이미지_프롬프트_스키마_fallbacks가_들어간다() {
        MessageCreateParams params = ReadRequests.request("model-x", IMAGE, "image/png", 48, TEMPLATE);

        assertThat(params.model().asString()).isEqualTo("model-x");
        assertThat(params.maxTokens()).isEqualTo(ReadRequests.MAX_TOKENS);
        assertThat(params.betas().orElseThrow()).contains(AnthropicBeta.SERVER_SIDE_FALLBACK_2026_07_01);
        BetaOutputConfig output = params.outputConfig().orElseThrow();
        assertThat(output.effort()).contains(BetaOutputConfig.Effort.LOW);
        assertThat(output.format()).isPresent();
        String body = params._body().toString();
        assertThat(body).contains("fallbacks");
        assertThat(body).contains(Base64.getEncoder().encodeToString(IMAGE)).contains("image/png");
        assertThat(body).contains("번호 1~48의 손글씨를 옮겨 적어라").doesNotContain("{{itemCount}}");
        // SDK 본문 표기(toString): 스키마의 세 칸과 required
        assertThat(body).contains("unreadable={type=boolean}").contains("required=[no, text, unreadable]");
        assertThat(body).contains("fallbacks=BetaFallbacksParam{default_=default}");
    }

    @Test
    void 실제_프롬프트_파일의_문항_수_자리가_모두_바뀐다() {
        String body = ReadRequests.request("m", IMAGE, "image/jpeg", 48, ReadRequests.loadPrompt())
                ._body()
                .toString();

        assertThat(body).doesNotContain("{{itemCount}}").contains("1번부터 48번까지 정확히 48개");
    }

    @Test
    void 프롬프트_파일에는_정답_자리가_없고_원칙이_들어_있다() {
        String template = ReadRequests.loadPrompt();

        assertThat(template).contains("{{itemCount}}");
        assertThat(template).doesNotContain("{{answers}}");
        assertThat(template).contains("고치지").contains("지시");
    }

    @Test
    void 정상_응답은_칸_목록과_모델_토큰을_돌려준다() {
        ReadResult result = ReadRequests.parse(
                "end_turn",
                "{\"items\":[{\"no\":2,\"text\":\"library\",\"unreadable\":false},"
                        + "{\"no\":1,\"text\":\"beautful\",\"unreadable\":false}]}",
                "model-y",
                1200,
                300);

        assertThat(result.items())
                .containsExactly(new ReadItem(2, "library", false), new ReadItem(1, "beautful", false));
        assertThat(result.model()).isEqualTo("model-y");
        assertThat(result.promptVersion()).isEqualTo(ReadRequests.PROMPT_VERSION);
        assertThat(result.inputTokens()).isEqualTo(1200);
        assertThat(result.outputTokens()).isEqualTo(300);
    }

    @Test
    void 거절_잘림_깨진_형식은_실패_종류로() {
        assertKind(() -> ReadRequests.parse("refusal", "", "m", 0, 0), ReadFailure.Kind.REFUSED);
        assertKind(() -> ReadRequests.parse("max_tokens", "{\"items\":[", "m", 0, 0), ReadFailure.Kind.TRUNCATED);
        assertKind(() -> ReadRequests.parse("end_turn", "not json", "m", 0, 0), ReadFailure.Kind.MALFORMED);
        assertKind(() -> ReadRequests.parse("end_turn", "{\"rows\":[]}", "m", 0, 0), ReadFailure.Kind.MALFORMED);
        assertKind(
                () -> ReadRequests.parse("end_turn", "{\"items\":[{\"no\":\"one\"}]}", "m", 0, 0),
                ReadFailure.Kind.MALFORMED);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(
            strings = {
                "",
                "{\"items\":[1]}",
                "{\"items\":[{\"no\":1.5,\"text\":\"a\",\"unreadable\":false}]}",
                "{\"items\":[{\"no\":1,\"text\":5,\"unreadable\":false}]}",
                "{\"items\":[{\"no\":1,\"text\":null,\"unreadable\":false}]}",
                "{\"items\":[{\"no\":1,\"unreadable\":false}]}",
                "{\"items\":[{\"no\":1,\"text\":\"a\",\"unreadable\":\"true\"}]}",
                "{\"items\":[{\"no\":1,\"text\":\"a\"}]}",
                "{\"items\":[{\"no\":99999999999,\"text\":\"a\",\"unreadable\":false}]}",
            })
    void 칸이_빠지거나_타입이_다르면_빈칸으로_넘기지_않고_MALFORMED(String body) {
        assertKind(() -> ReadRequests.parse("end_turn", body, "m", 0, 0), ReadFailure.Kind.MALFORMED);
    }

    @Test
    void 실패_메시지에_응답_원문을_넣지_않는다() {
        assertThatThrownBy(() -> ReadRequests.parse("end_turn", "secret-handwriting", "m", 0, 0))
                .hasMessageNotContaining("secret-handwriting");
    }

    private static void assertKind(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ReadFailure.Kind kind) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(
                        ReadFailure.class, failure -> assertThat(failure.kind()).isEqualTo(kind));
    }
}
