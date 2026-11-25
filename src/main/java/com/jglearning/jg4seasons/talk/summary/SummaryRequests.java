package com.jglearning.jg4seasons.talk.summary;

import com.anthropic.core.JsonValue;
import com.anthropic.models.beta.AnthropicBeta;
import com.anthropic.models.beta.messages.BetaJsonOutputFormat;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.jglearning.jg4seasons.talk.TalkLevel;
import com.jglearning.jg4seasons.talk.TalkTranscript.Turn;
import com.jglearning.jg4seasons.talk.summary.TalkSummary.Correction;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 요약 요청 조립·응답 해석(순수 함수, 답안지 읽기 ReadRequests와 같은 SDK 사용법). beta 메시지 엔드포인트 + 구조화 응답(JSON 스키마) + effort low + 거절 시 서버 측
 * fallbacks. 글자 기록은 &lt;transcript&gt; 안에 데이터로 넣고 프롬프트가 그 안의 문장을 지시로 따르지 않게 한다
 */
public final class SummaryRequests {

    /** talk_session에는 남기지 않는다(요약은 결과만). 프롬프트를 바꾸면 v2 파일로 */
    static final String PROMPT_PATH = "/prompts/talk-summary/v1.txt";

    /** 짧은 JSON(표현 2개 + 고침 3개) + low effort 생각 */
    static final int MAX_TOKENS = 4096;

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private SummaryRequests() {}

    public static String loadPrompt() {
        try (InputStream in = SummaryRequests.class.getResourceAsStream(PROMPT_PATH)) {
            if (in == null) {
                throw new IllegalStateException("요약 프롬프트 파일이 없다: " + PROMPT_PATH);
            }
            String prompt = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            if (prompt.isBlank()) {
                throw new IllegalStateException("요약 프롬프트 파일이 비었다: " + PROMPT_PATH);
            }
            return prompt;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static MessageCreateParams request(String model, String promptTemplate, TalkLevel level, List<Turn> turns) {
        String transcript = turns.stream()
                .map(turn -> turn.role().code() + ": " + neutralize(turn.text()))
                .collect(Collectors.joining("\n"));
        String prompt = promptTemplate.replace("{{level}}", level.slug()).replace("{{transcript}}", transcript);
        return MessageCreateParams.builder()
                .model(model)
                .maxTokens(MAX_TOKENS)
                .addBeta(AnthropicBeta.SERVER_SIDE_FALLBACK_2026_07_01)
                .fallbacksDefault()
                .outputConfig(BetaOutputConfig.builder()
                        .effort(BetaOutputConfig.Effort.LOW)
                        .format(BetaJsonOutputFormat.builder().schema(schema()).build())
                        .build())
                .addUserMessage(prompt)
                .build();
    }

    /** 학생 글자에 들어온 닫는 태그가 데이터 구역을 끝내지 못하게(학생이 지시를 끼워 넣는 것 방지) */
    private static String neutralize(String text) {
        return text.replaceAll("(?i)</?transcript>", "[transcript]");
    }

    /** {goodExpressions:[string], corrections:[{said, better, note}]} — 모든 칸 필수, 다른 칸 없음 */
    private static BetaJsonOutputFormat.Schema schema() {
        Map<String, Object> correction = Map.of(
                "type",
                "object",
                "properties",
                Map.of(
                        "said", Map.of("type", "string"),
                        "better", Map.of("type", "string"),
                        "note", Map.of("type", "string")),
                "required",
                List.of("said", "better", "note"),
                "additionalProperties",
                false);
        return BetaJsonOutputFormat.Schema.builder()
                .putAdditionalProperty("type", JsonValue.from("object"))
                .putAdditionalProperty(
                        "properties",
                        JsonValue.from(Map.of(
                                "goodExpressions", Map.of("type", "array", "items", Map.of("type", "string")),
                                "corrections", Map.of("type", "array", "items", correction))))
                .putAdditionalProperty("required", JsonValue.from(List.of("goodExpressions", "corrections")))
                .putAdditionalProperty("additionalProperties", JsonValue.from(false))
                .build();
    }

    /** 응답 해석. 거절·잘림·JSON 아님·칸 누락·타입 다름은 모두 empty(요약 없음) */
    public static Optional<TalkSummary> parse(String stopReason, String text) {
        if ("refusal".equals(stopReason) || "max_tokens".equals(stopReason)) {
            return Optional.empty();
        }
        JsonNode root;
        try {
            root = JSON.readTree(text == null ? "" : text);
        } catch (JacksonException e) {
            return Optional.empty();
        }
        if (root == null || !root.isObject()) {
            return Optional.empty();
        }
        JsonNode good = root.get("goodExpressions");
        JsonNode corrections = root.get("corrections");
        if (good == null || !good.isArray() || corrections == null || !corrections.isArray()) {
            return Optional.empty();
        }
        List<String> expressions = new ArrayList<>();
        for (JsonNode expression : good) {
            if (!expression.isString()) {
                return Optional.empty();
            }
            expressions.add(expression.asString());
        }
        List<Correction> fixes = new ArrayList<>();
        for (JsonNode correction : corrections) {
            JsonNode said = correction.get("said");
            JsonNode better = correction.get("better");
            JsonNode note = correction.get("note");
            if (said == null
                    || !said.isString()
                    || better == null
                    || !better.isString()
                    || note == null
                    || !note.isString()) {
                return Optional.empty();
            }
            fixes.add(new Correction(said.asString(), better.asString(), note.asString()));
        }
        return Optional.of(new TalkSummary(expressions, fixes));
    }
}
