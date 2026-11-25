package com.jglearning.jg4seasons.talk.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/** 임시 키 발급 요청·응답 모양(공식 문서 client_secrets). 외부 호출 없이 가짜 서버로 */
class OpenAiRealtimeSessionIssuerTest {

    static final RealtimeSessionRequest REQUEST =
            new RealtimeSessionRequest("gpt-realtime-2.1-mini", "Be kind.", "marin", "gpt-4o-mini-transcribe", 60);

    MockRestServiceServer server;
    OpenAiRealtimeSessionIssuer issuer;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        issuer = new OpenAiRealtimeSessionIssuer(builder, JsonMapper.builder().build(), "sk-test-key");
    }

    @Test
    void 요청_본문이_문서_모양이다() {
        server.expect(requestTo("https://api.openai.com/v1/realtime/client_secrets"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer sk-test-key"))
                .andExpect(jsonPath("$.expires_after.anchor").value("created_at"))
                .andExpect(jsonPath("$.expires_after.seconds").value(60))
                .andExpect(jsonPath("$.session.type").value("realtime"))
                .andExpect(jsonPath("$.session.model").value("gpt-realtime-2.1-mini"))
                .andExpect(jsonPath("$.session.instructions").value("Be kind."))
                .andExpect(jsonPath("$.session.audio.output.voice").value("marin"))
                .andExpect(jsonPath("$.session.audio.input.transcription.model").value("gpt-4o-mini-transcribe"))
                .andExpect(jsonPath("$.session.audio.input.turn_detection.type").value("server_vad"))
                .andExpect(jsonPath("$.session.audio.input.turn_detection.idle_timeout_ms")
                        .value(5000))
                .andRespond(withSuccess(
                        "{\"value\":\"ek_abc\",\"expires_at\":1764122460,\"session\":{}}", MediaType.APPLICATION_JSON));

        issuer.issue(REQUEST);

        server.verify();
    }

    @Test
    void 응답의_value와_expires_at을_읽는다() {
        server.expect(requestTo("https://api.openai.com/v1/realtime/client_secrets"))
                .andRespond(withSuccess(
                        "{\"value\":\"ek_abc\",\"expires_at\":1764122460,\"session\":{\"type\":\"realtime\"}}",
                        MediaType.APPLICATION_JSON));

        IssuedSecret secret = issuer.issue(REQUEST);

        assertThat(secret.clientSecret()).isEqualTo("ek_abc");
        assertThat(secret.expiresAt()).isEqualTo(Instant.ofEpochSecond(1764122460));
        assertThat(secret.model()).isEqualTo("gpt-realtime-2.1-mini");
        assertThat(secret.toString()).doesNotContain("ek_abc");
    }

    @Test
    void 서버_오류면_RealtimeUnavailable() {
        server.expect(requestTo("https://api.openai.com/v1/realtime/client_secrets"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> issuer.issue(REQUEST)).isInstanceOf(RealtimeUnavailableException.class);
    }

    @Test
    void 키가_틀리면_RealtimeUnavailable이고_메시지에_키가_없다() {
        server.expect(requestTo("https://api.openai.com/v1/realtime/client_secrets"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"Incorrect API key provided: sk-test-key\"}}"));

        assertThatThrownBy(() -> issuer.issue(REQUEST))
                .isInstanceOf(RealtimeUnavailableException.class)
                .satisfies(e -> assertThat(String.valueOf(e.getMessage())).doesNotContain("sk-test-key"))
                .satisfies(e -> assertThat(String.valueOf(e.getCause())).doesNotContain("sk-test-key"));
    }

    @Test
    void value나_expires_at이_없으면_RealtimeUnavailable() {
        server.expect(requestTo("https://api.openai.com/v1/realtime/client_secrets"))
                .andRespond(withSuccess("{\"expires_at\":1764122460}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> issuer.issue(REQUEST)).isInstanceOf(RealtimeUnavailableException.class);

        server.reset();
        server.expect(requestTo("https://api.openai.com/v1/realtime/client_secrets"))
                .andRespond(withSuccess("{\"value\":\"ek_abc\"}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> issuer.issue(REQUEST)).isInstanceOf(RealtimeUnavailableException.class);
    }

    @Test
    void 연결이_끊기거나_시간이_넘으면_RealtimeUnavailable이고_가장_안쪽_원인_이름이_남는다() {
        // 리뷰 7·10: 응답 없는 실패(ResourceAccessException으로 감싸 옴) — 시간 초과·연결 거부를 로그에서 가릴 수 있게
        server.expect(requestTo("https://api.openai.com/v1/realtime/client_secrets"))
                .andRespond(request -> {
                    throw new java.net.SocketTimeoutException("read timed out");
                });

        assertThatThrownBy(() -> issuer.issue(REQUEST))
                .isInstanceOf(RealtimeUnavailableException.class)
                .hasMessageContaining("SocketTimeoutException");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(
            strings = {
                "{\"value\":\"\",\"expires_at\":1764122460}",
                "{\"value\":123,\"expires_at\":1764122460}",
                "{\"value\":\"ek_abc\",\"expires_at\":\"1764122460\"}",
                "{\"value\":\"ek_abc\",\"expires_at\":1.5}",
                "[]",
                ""
            })
    void value나_expires_at의_형식이_다르면_RealtimeUnavailable(String body) {
        // 리뷰 7: 빈 키·1970년 만료 시각이 학생에게 201로 내려가지 않게
        server.expect(requestTo("https://api.openai.com/v1/realtime/client_secrets"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> issuer.issue(REQUEST)).isInstanceOf(RealtimeUnavailableException.class);
    }

    @Test
    void JSON이_아니면_RealtimeUnavailable() {
        server.expect(requestTo("https://api.openai.com/v1/realtime/client_secrets"))
                .andRespond(withSuccess("<html>gateway</html>", MediaType.TEXT_HTML));

        assertThatThrownBy(() -> issuer.issue(REQUEST)).isInstanceOf(RealtimeUnavailableException.class);
    }
}
