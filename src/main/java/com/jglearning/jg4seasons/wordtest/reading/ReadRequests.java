package com.jglearning.jg4seasons.wordtest.reading;

import com.anthropic.core.JsonValue;
import com.anthropic.models.beta.AnthropicBeta;
import com.anthropic.models.beta.messages.BetaBase64ImageSource;
import com.anthropic.models.beta.messages.BetaContentBlockParam;
import com.anthropic.models.beta.messages.BetaImageBlockParam;
import com.anthropic.models.beta.messages.BetaJsonOutputFormat;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.MessageCreateParams;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 답안지 읽기 요청 만들기와 응답 해석(순수 — 네트워크 없음). 프롬프트에는 문항 수만 넣는다(정답 목록·인쇄된 뜻은 넣지 않음). 응답은 구조화 응답(JSON 스키마)으로 받고 여기서 다시
 * 해석한다. 실패 메시지에 응답 원문을 넣지 않는다
 */
public final class ReadRequests {

    public static final String PROMPT_VERSION = "v1";
    static final String PROMPT_PATH = "/prompts/word-test-read/" + PROMPT_VERSION + ".txt";
    /** 48문항 JSON(약 1,500) + low effort 생각. 잘리면 TRUNCATED로 재시도 */
    static final long MAX_TOKENS = 8_000;

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private ReadRequests() {}

    /** 클래스패스의 프롬프트 파일({{itemCount}} 자리 포함) */
    public static String loadPrompt() {
        try (InputStream in = ReadRequests.class.getResourceAsStream(PROMPT_PATH)) {
            if (in == null) {
                throw new IllegalStateException("프롬프트 파일이 없다: " + PROMPT_PATH);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** beta 엔드포인트 요청: 이미지 + 프롬프트, effort low, 구조화 응답, 거절 시 다른 모델로(서버 측 fallbacks) */
    public static MessageCreateParams request(
            String model, byte[] image, String mediaType, int itemCount, String promptTemplate) {
        BetaImageBlockParam imageBlock = BetaImageBlockParam.builder()
                .source(BetaBase64ImageSource.builder()
                        .data(Base64.getEncoder().encodeToString(image))
                        .mediaType(
                                "image/png".equals(mediaType)
                                        ? BetaBase64ImageSource.MediaType.IMAGE_PNG
                                        : BetaBase64ImageSource.MediaType.IMAGE_JPEG)
                        .build())
                .build();
        String prompt = promptTemplate.replace("{{itemCount}}", Integer.toString(itemCount));
        return MessageCreateParams.builder()
                .model(model)
                .maxTokens(MAX_TOKENS)
                .addBeta(AnthropicBeta.SERVER_SIDE_FALLBACK_2026_07_01)
                .fallbacksDefault()
                .outputConfig(BetaOutputConfig.builder()
                        .effort(BetaOutputConfig.Effort.LOW)
                        .format(BetaJsonOutputFormat.builder().schema(schema()).build())
                        .build())
                .addUserMessageOfContentBlockParams(
                        List.of(BetaContentBlockParam.ofImage(imageBlock), BetaContentBlockParam.ofText(prompt)))
                .build();
    }

    /** {items:[{no:int, text:string, unreadable:bool}]} — 모든 칸 필수, 다른 칸 없음 */
    private static BetaJsonOutputFormat.Schema schema() {
        Map<String, Object> item = Map.of(
                "type",
                "object",
                "properties",
                Map.of(
                        "no", Map.of("type", "integer"),
                        "text", Map.of("type", "string"),
                        "unreadable", Map.of("type", "boolean")),
                "required",
                List.of("no", "text", "unreadable"),
                "additionalProperties",
                false);
        return BetaJsonOutputFormat.Schema.builder()
                .putAdditionalProperty("type", JsonValue.from("object"))
                .putAdditionalProperty(
                        "properties", JsonValue.from(Map.of("items", Map.of("type", "array", "items", item))))
                .putAdditionalProperty("required", JsonValue.from(List.of("items")))
                .putAdditionalProperty("additionalProperties", JsonValue.from(false))
                .build();
    }

    /**
     * 응답 해석. stopReason은 SDK 값의 문자열(refusal·max_tokens·end_turn …), text는 응답 글자(JSON).
     *
     * @throws ReadFailure REFUSED·TRUNCATED·MALFORMED
     */
    public static ReadResult parse(String stopReason, String text, String model, long inputTokens, long outputTokens) {
        if ("refusal".equals(stopReason)) {
            throw new ReadFailure(ReadFailure.Kind.REFUSED, "모델이 응답을 거절했다");
        }
        if ("max_tokens".equals(stopReason)) {
            throw new ReadFailure(ReadFailure.Kind.TRUNCATED, "응답이 max_tokens에서 잘렸다");
        }
        JsonNode root;
        try {
            root = JSON.readTree(text == null ? "" : text);
        } catch (JacksonException e) {
            throw new ReadFailure(ReadFailure.Kind.MALFORMED, "응답이 JSON이 아니다");
        }
        JsonNode items = root == null ? null : root.get("items");
        if (items == null || !items.isArray()) {
            throw new ReadFailure(ReadFailure.Kind.MALFORMED, "items 배열이 없다");
        }
        List<ReadItem> result = new ArrayList<>();
        for (JsonNode item : items) {
            JsonNode no = item.get("no");
            JsonNode value = item.get("text");
            JsonNode unreadable = item.get("unreadable");
            if (no == null || !no.isIntegralNumber() || (value != null && !value.isNull() && !value.isString())) {
                throw new ReadFailure(ReadFailure.Kind.MALFORMED, "items의 칸 형식이 다르다");
            }
            result.add(new ReadItem(
                    no.asInt(),
                    value == null || value.isNull() ? null : value.asString(),
                    unreadable != null && unreadable.isBoolean() && unreadable.booleanValue()));
        }
        return new ReadResult(List.copyOf(result), model, PROMPT_VERSION, inputTokens, outputTokens);
    }
}
