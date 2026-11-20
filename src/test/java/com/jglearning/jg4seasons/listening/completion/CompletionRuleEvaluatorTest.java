package com.jglearning.jg4seasons.listening.completion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 완료 규칙 해석(ADR-07·27·34): 지문마다 필수 목소리 중 하나를 청취율 기준 이상 들었거나, 합산이 켜져 있고 여러 목소리로 들은 구간을 합친 청취율이 기준 이상이면 그 지문
 * 완료.
 */
class CompletionRuleEvaluatorTest {

    static final BigDecimal THRESHOLD = new BigDecimal("0.900");
    /** V4 규칙(전 레벨) */
    static final String V4_RULE =
            "[{\"kind\":\"passage\",\"eachPassage\":true,\"anyOf\":[\"us_w\",\"us_m\",\"uk_w\",\"uk_m\"]}]";
    /** V10 규칙(전 레벨): 목소리끼리 합친다 */
    static final String V10_RULE = "[{\"kind\":\"passage\",\"eachPassage\":true,"
            + "\"anyOf\":[\"us_w\",\"us_m\",\"uk_w\",\"uk_m\"],\"combine\":true}]";
    /** 목소리 하나만 필수인 규칙(필수가 아닌 목소리 확인용) */
    static final String ONE_VOICE_RULE = "[{\"kind\":\"passage\",\"eachPassage\":true,\"anyOf\":[\"us_w\"]}]";

    @Test
    void V4_규칙을_읽는다() {
        CompletionRule rule = CompletionRule.parse(THRESHOLD, V4_RULE);

        assertThat(rule.threshold()).isEqualByComparingTo("0.9");
        assertThat(rule.requiredVoices()).containsExactlyInAnyOrder("us_w", "us_m", "uk_w", "uk_m");
        assertThat(CompletionRuleEvaluator.isRequired(rule, "uk_m")).isTrue();
        assertThat(CompletionRuleEvaluator.isRequired(CompletionRule.parse(THRESHOLD, ONE_VOICE_RULE), "uk_m"))
                .isFalse();
    }

    @Test
    void 임계값_경계() {
        CompletionRule rule = CompletionRule.parse(THRESHOLD, V4_RULE);

        assertThat(CompletionRuleEvaluator.isTrackComplete(rule, new BigDecimal("0.8999")))
                .isFalse();
        assertThat(CompletionRuleEvaluator.isTrackComplete(rule, new BigDecimal("0.9000")))
                .isTrue();
        assertThat(CompletionRuleEvaluator.isTrackComplete(rule, BigDecimal.ONE))
                .isTrue();
        assertThat(CompletionRuleEvaluator.isTrackComplete(rule, BigDecimal.ZERO))
                .isFalse();
    }

    @Test
    void combine이_없으면_합치지_않는다() {
        assertThat(CompletionRule.parse(THRESHOLD, V4_RULE).combineVoices()).isFalse();
    }

    @Test
    void combine을_읽는다() {
        assertThat(CompletionRule.parse(THRESHOLD, V10_RULE).combineVoices()).isTrue();
        assertThat(CompletionRule.parse(THRESHOLD, V10_RULE.replace("\"combine\":true", "\"combine\":false"))
                        .combineVoices())
                .isFalse();
    }

    @Test
    void combine이_불리언이_아니면_IllegalState() {
        for (String value : new String[] {"\"true\"", "1", "null", "[]"}) {
            assertThatThrownBy(() -> CompletionRule.parse(
                            THRESHOLD, V10_RULE.replace("\"combine\":true", "\"combine\":" + value)))
                    .as(value)
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void 모르는_키는_IllegalState() {
        // 오타(combined)가 조용히 "합치지 않음"이 되지 않게
        assertThatThrownBy(() ->
                        CompletionRule.parse(THRESHOLD, V10_RULE.replace("\"combine\":true", "\"combined\":true")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("combined");
    }

    @Test
    void 필수_트랙이_완료면_합친_청취율과_상관없이_지문_완료() {
        for (String json : new String[] {V4_RULE, V10_RULE}) {
            CompletionRule rule = CompletionRule.parse(THRESHOLD, json);

            assertThat(CompletionRuleEvaluator.isPassageComplete(rule, true, BigDecimal.ZERO))
                    .isTrue();
        }
    }

    @Test
    void 합산이_켜져_있으면_합친_청취율이_기준_이상일_때_지문_완료() {
        CompletionRule rule = CompletionRule.parse(THRESHOLD, V10_RULE);

        assertThat(CompletionRuleEvaluator.isPassageComplete(rule, false, new BigDecimal("0.8999")))
                .isFalse();
        assertThat(CompletionRuleEvaluator.isPassageComplete(rule, false, new BigDecimal("0.9000")))
                .as("기준과 같으면 완료")
                .isTrue();
        assertThat(CompletionRuleEvaluator.isPassageComplete(rule, false, BigDecimal.ONE))
                .isTrue();
        assertThat(CompletionRuleEvaluator.isPassageComplete(rule, false, BigDecimal.ZERO))
                .isFalse();
    }

    @Test
    void 합산이_꺼져_있으면_합친_청취율이_1이어도_미완료() {
        CompletionRule rule = CompletionRule.parse(THRESHOLD, V4_RULE);

        assertThat(CompletionRuleEvaluator.isPassageComplete(rule, false, BigDecimal.ONE))
                .isFalse();
    }

    @Test
    void 모르는_kind는_IllegalState() {
        assertThatThrownBy(() -> CompletionRule.parse(THRESHOLD, "[{\"kind\":\"word\",\"anyOf\":[\"us_w\"]}]"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 지문마다가_아니면_IllegalState() {
        assertThatThrownBy(() -> CompletionRule.parse(
                        THRESHOLD, "[{\"kind\":\"passage\",\"eachPassage\":false,\"anyOf\":[\"us_w\"]}]"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void anyOf가_없거나_비면_IllegalState() {
        assertThatThrownBy(() -> CompletionRule.parse(THRESHOLD, "[{\"kind\":\"passage\",\"eachPassage\":true}]"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() ->
                        CompletionRule.parse(THRESHOLD, "[{\"kind\":\"passage\",\"eachPassage\":true,\"anyOf\":[]}]"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() ->
                        CompletionRule.parse(THRESHOLD, "[{\"kind\":\"passage\",\"eachPassage\":true,\"anyOf\":[1]}]"))
                .as("문자열이 아닌 속도")
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 규칙이_없거나_JSON이_깨지면_IllegalState() {
        assertThatThrownBy(() -> CompletionRule.parse(THRESHOLD, "[]")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> CompletionRule.parse(THRESHOLD, "{not json"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 임계값은_0보다_크고_1_이하() {
        assertThatThrownBy(() -> CompletionRule.parse(BigDecimal.ZERO, V4_RULE))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> CompletionRule.parse(new BigDecimal("1.001"), V4_RULE))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 목소리_표기가_트랙과_다르면_IllegalState() {
        // 속도(V3까지의 1.0)도 이제 형식 오류 — 남아 있으면 어떤 트랙도 필수가 아니게 되어 회차가 끝나지 않는다
        for (String voice : new String[] {"1.0", "en_us_w", "US_W", "us_x", " us_w", "us_w "}) {
            assertThatThrownBy(() -> CompletionRule.parse(
                            THRESHOLD, "[{\"kind\":\"passage\",\"eachPassage\":true,\"anyOf\":[\"" + voice + "\"]}]"))
                    .as(voice)
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void 생성자로_만들어도_같은_검사를_한다() {
        assertThatThrownBy(() -> new CompletionRule(new BigDecimal("5"), Set.of("us_w"), true))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new CompletionRule(null, Set.of("us_w"), true))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new CompletionRule(THRESHOLD, Set.of(), true))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new CompletionRule(THRESHOLD, Set.of("1"), true))
                .isInstanceOf(IllegalStateException.class);
    }
}
