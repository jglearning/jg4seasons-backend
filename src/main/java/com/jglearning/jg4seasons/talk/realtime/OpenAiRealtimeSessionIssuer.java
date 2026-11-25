package com.jglearning.jg4seasons.talk.realtime;

import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * OpenAI Realtime 임시 키 발급(POST /v1/realtime/client_secrets, 공식 문서 11/25 확인). 지시문·음성·학생 말 글자 변환·침묵 뒤 다시 묻기(idle_timeout_ms
 * 5000 — 기획 §3 "5초 넘게 말이 없으면")를 키에 묶는다. 서버 키는 이 요청에만 쓰고 브라우저에는 임시 키만 간다.
 *
 * <p>로그에는 상태 코드만 남긴다 — 응답 본문(오류 메시지에 키 일부가 들어 있을 수 있음)·지시문·임시 키는 남기지 않는다. 재시도는 하지 않는다(학생이 다시 누른다)
 */
public class OpenAiRealtimeSessionIssuer implements RealtimeSessionIssuer {

    private static final Logger log = LoggerFactory.getLogger(OpenAiRealtimeSessionIssuer.class);

    static final String BASE_URL = "https://api.openai.com";
    static final String PATH = "/v1/realtime/client_secrets";
    static final int IDLE_TIMEOUT_MS = 5000;

    /** 학생 말 받아쓰기 언어(영어 말하기 연습 — 한국어 섞인 발화를 다른 언어로 잘못 옮기지 않게, 프론트 요청 #86) */
    static final String TRANSCRIPTION_LANGUAGE = "en";

    /** 폰 스피커로 대화하는 경우(far_field). 헤드셋이면 near_field가 맞지만 시연 기기는 폰 스피커(프론트 요청 #86) */
    static final String NOISE_REDUCTION = "far_field";

    private final RestClient client;
    private final JsonMapper json;

    /** builder의 요청 팩터리(시간 제한)는 부르는 쪽이 정한다 — 테스트는 가짜 서버를 묶는다 */
    public OpenAiRealtimeSessionIssuer(RestClient.Builder builder, JsonMapper json, String apiKey) {
        this.client = builder.baseUrl(BASE_URL)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
        this.json = json;
    }

    @Override
    public IssuedSecret issue(RealtimeSessionRequest request) {
        String body;
        try {
            body = client.post()
                    .uri(PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json.writeValueAsString(requestBody(request)))
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            log.warn("임시 키 발급 실패: HTTP {}", e.getStatusCode().value());
            throw new RealtimeUnavailableException(
                    "임시 키 발급 실패: HTTP " + e.getStatusCode().value());
        } catch (RestClientException e) {
            // 시간 초과·연결 거부·DNS가 모두 ResourceAccessException으로 감싸 온다 — 가장 안쪽 원인 이름까지(클래스 이름이라 키·본문 없음, 리뷰 10)
            String cause = e.getClass().getSimpleName() + "/"
                    + e.getMostSpecificCause().getClass().getSimpleName();
            log.warn("임시 키 발급 실패: {}", cause);
            throw new RealtimeUnavailableException("임시 키 발급 실패: " + cause);
        }
        return parse(body, request.model());
    }

    private static Map<String, Object> requestBody(RealtimeSessionRequest request) {
        return Map.of(
                "expires_after", Map.of("anchor", "created_at", "seconds", request.keyTtlSeconds()),
                "session",
                        Map.of(
                                "type", "realtime",
                                "model", request.model(),
                                "instructions", request.instructions(),
                                "audio",
                                        Map.of(
                                                "input",
                                                        Map.of(
                                                                "transcription",
                                                                Map.of(
                                                                        "model",
                                                                        request.transcriptionModel(),
                                                                        "language",
                                                                        TRANSCRIPTION_LANGUAGE),
                                                                "noise_reduction",
                                                                Map.of("type", NOISE_REDUCTION),
                                                                "turn_detection",
                                                                Map.of(
                                                                        "type",
                                                                        "server_vad",
                                                                        "idle_timeout_ms",
                                                                        IDLE_TIMEOUT_MS)),
                                                "output", Map.of("voice", request.voice()))));
    }

    /** value(ek_…)와 expires_at(유닉스 초)만 쓴다. 없거나 형식이 다르면 503 — 500으로 새지 않게 */
    private IssuedSecret parse(String body, String model) {
        JsonNode root;
        try {
            root = body == null ? null : json.readTree(body);
        } catch (JacksonException e) {
            log.warn("임시 키 응답이 JSON이 아니다");
            throw new RealtimeUnavailableException("임시 키 응답이 JSON이 아니다");
        }
        JsonNode value = root == null ? null : root.get("value");
        JsonNode expiresAt = root == null ? null : root.get("expires_at");
        if (value == null || !value.isString() || value.asString().isBlank()) {
            log.warn("임시 키 응답에 value가 없다");
            throw new RealtimeUnavailableException("임시 키 응답에 value가 없다");
        }
        if (expiresAt == null || !expiresAt.isIntegralNumber()) {
            log.warn("임시 키 응답에 expires_at이 없다");
            throw new RealtimeUnavailableException("임시 키 응답에 expires_at이 없다");
        }
        return new IssuedSecret(value.asString(), Instant.ofEpochSecond(expiresAt.asLong()), model);
    }
}
