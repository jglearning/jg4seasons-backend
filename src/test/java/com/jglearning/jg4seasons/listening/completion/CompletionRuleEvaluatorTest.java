package com.jglearning.jg4seasons.listening.completion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 완료 규칙 해석(ADR-07): 지문마다 필수 속도 중 하나를 청취율 기준 이상 들으면 그 지문 완료. */
class CompletionRuleEvaluatorTest {

    static final BigDecimal THRESHOLD = new BigDecimal("0.900");
    static final String L2_RULE = "[{\"kind\":\"passage\",\"eachPassage\":true,\"anyOf\":[\"1.0\"]}]";
    static final String L1_RULE = "[{\"kind\":\"passage\",\"eachPassage\":true,\"anyOf\":[\"0.8\",\"1.0\"]}]";

    @Test
    void V2_규칙을_읽는다() {
        CompletionRule rule = CompletionRule.parse(THRESHOLD, L2_RULE);

        assertThat(rule.threshold()).isEqualByComparingTo("0.9");
        assertThat(rule.requiredSpeeds()).containsExactly("1.0");
        assertThat(CompletionRuleEvaluator.isRequired(rule, "1.0")).isTrue();
        assertThat(CompletionRuleEvaluator.isRequired(rule, "0.8")).isFalse();
        assertThat(CompletionRule.parse(THRESHOLD, L1_RULE).requiredSpeeds()).containsExactlyInAnyOrder("0.8", "1.0");
    }

    @Test
    void 임계값_경계() {
        CompletionRule rule = CompletionRule.parse(THRESHOLD, L2_RULE);

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
    void 필수_속도_하나만_완료해도_지문_완료() {
        CompletionRule rule = CompletionRule.parse(THRESHOLD, L1_RULE);

        assertThat(CompletionRuleEvaluator.isPassageComplete(rule, Set.of("0.8")))
                .isTrue();
        assertThat(CompletionRuleEvaluator.isPassageComplete(rule, Set.of("1.0")))
                .isTrue();
    }

    @Test
    void 필수가_아닌_속도만_완료면_미완료() {
        CompletionRule rule = CompletionRule.parse(THRESHOLD, L2_RULE);

        assertThat(CompletionRuleEvaluator.isPassageComplete(rule, Set.of("1.2")))
                .isFalse();
        assertThat(CompletionRuleEvaluator.isPassageComplete(rule, Set.of())).isFalse();
    }

    @Test
    void 모르는_kind는_IllegalState() {
        assertThatThrownBy(() -> CompletionRule.parse(THRESHOLD, "[{\"kind\":\"word\",\"anyOf\":[\"1.0\"]}]"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 지문마다가_아니면_IllegalState() {
        assertThatThrownBy(() -> CompletionRule.parse(
                        THRESHOLD, "[{\"kind\":\"passage\",\"eachPassage\":false,\"anyOf\":[\"1.0\"]}]"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void anyOf가_없거나_비면_IllegalState() {
        assertThatThrownBy(() -> CompletionRule.parse(THRESHOLD, "[{\"kind\":\"passage\",\"eachPassage\":true}]"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() ->
                        CompletionRule.parse(THRESHOLD, "[{\"kind\":\"passage\",\"eachPassage\":true,\"anyOf\":[]}]"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> CompletionRule.parse(
                        THRESHOLD, "[{\"kind\":\"passage\",\"eachPassage\":true,\"anyOf\":[1.0]}]"))
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
        assertThatThrownBy(() -> CompletionRule.parse(BigDecimal.ZERO, L2_RULE))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> CompletionRule.parse(new BigDecimal("1.001"), L2_RULE))
                .isInstanceOf(IllegalStateException.class);
    }
}
