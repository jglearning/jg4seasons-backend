package com.jglearning.jg4seasons.wordtest;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.wordtest.reading.AnswerSheetReader;
import com.jglearning.jg4seasons.wordtest.reading.AnthropicAnswerSheetReader;
import org.junit.jupiter.api.Test;

/** 읽기 구현 고르기: none·fake 설정은 꺼진 구현(워커가 작업을 잡지 않음), anthropic은 실제 구현(네트워크 호출 없이 만들기만) */
class WordTestReaderConfigTest {

    final WordTestReaderConfig config = new WordTestReaderConfig();

    @Test
    void 꺼짐_설정이면_enabled가_false() {
        assertThat(reader(WordTestReaderProperties.Type.NONE, null).enabled()).isFalse();
        assertThat(reader(WordTestReaderProperties.Type.FAKE, null).enabled()).isFalse();
        assertThat(reader(null, null).enabled()).as("설정이 없으면 none").isFalse();
    }

    @Test
    void anthropic이면_실제_구현() throws Exception {
        AnswerSheetReader reader = reader(WordTestReaderProperties.Type.ANTHROPIC, "test-key");

        assertThat(reader).isInstanceOf(AnthropicAnswerSheetReader.class);
        assertThat(reader.enabled()).isTrue();
        ((AnthropicAnswerSheetReader) reader).close();
    }

    private AnswerSheetReader reader(WordTestReaderProperties.Type type, String key) {
        return config.answerSheetReader(new WordTestReaderProperties(type, null, key));
    }
}
