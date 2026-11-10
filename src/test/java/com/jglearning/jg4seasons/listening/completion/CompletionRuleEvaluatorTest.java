package com.jglearning.jg4seasons.listening.completion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 완료 규칙 해석(ADR-07·27): 지문마다 필수 목소리 중 하나를 청취율 기준 이상 들으면 그 지문 완료. */
class CompletionRuleEvaluatorTest {

    static final BigDecimal THRESHOLD = new BigDecimal("0.900");
    /** V4 규칙(전 레벨) */
    static final String V4_RULE =
            "[{\"kind\":\"passage\",\"eachPassage\":true,\"anyOf\":[\"us_w\",\"us_m\",\"uk_w\",\"uk_m\"]}]";
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
    void 아무_목소리나_하나를_끝까지_들으면_지문_완료() {
        CompletionRule rule = CompletionRule.parse(THRESHOLD, V4_RULE);

        for (String voice : new String[] {"us_w", "us_m", "uk_w", "uk_m"}) {
            assertThat(CompletionRuleEvaluator.isPassageComplete(rule, Set.of(voice)))
                    .as(voice)
                    .isTrue();
        }
    }

    @Test
    void 필수가_아닌_목소리만_완료면_미완료() {
        CompletionRule rule = CompletionRule.parse(THRESHOLD, ONE_VOICE_RULE);

        assertThat(CompletionRuleEvaluator.isPassageComplete(rule, Set.of("uk_m")))
                .isFalse();
        assertThat(CompletionRuleEvaluator.isPassageComplete(rule, Set.of())).isFalse();
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
        assertThatThrownBy(() -> new CompletionRule(new BigDecimal("5"), Set.of("us_w")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new CompletionRule(null, Set.of("us_w"))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new CompletionRule(THRESHOLD, Set.of())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new CompletionRule(THRESHOLD, Set.of("1"))).isInstanceOf(IllegalStateException.class);
    }
}
