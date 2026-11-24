package com.jglearning.jg4seasons.wordtest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** 답안지 읽기 설정: 기본 꺼짐, anthropic이면 키 필수, 키는 문자열에 찍히지 않음 */
class WordTestReaderPropertiesTest {

    @Test
    void 기본은_꺼짐이고_모델은_Sonnet_5_5() {
        WordTestReaderProperties properties = new WordTestReaderProperties(null, null, null);

        assertThat(properties.type()).isEqualTo(WordTestReaderProperties.Type.NONE);
        assertThat(properties.model()).isEqualTo("claude-sonnet-5-5");
    }

    @Test
    void anthropic인데_키가_비면_서버가_뜨지_않는다() {
        assertThatThrownBy(() -> new WordTestReaderProperties(WordTestReaderProperties.Type.ANTHROPIC, null, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("api-key");
    }

    @Test
    void 키는_문자열에_찍히지_않는다() {
        WordTestReaderProperties properties =
                new WordTestReaderProperties(WordTestReaderProperties.Type.ANTHROPIC, "m", "sk-secret-value");

        assertThat(properties.toString()).doesNotContain("sk-secret-value");
    }
}
