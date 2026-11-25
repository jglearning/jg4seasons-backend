package com.jglearning.jg4seasons.talk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jglearning.jg4seasons.talk.TalkSummaryProperties.Type;
import org.junit.jupiter.api.Test;

class TalkSummaryPropertiesTest {

    @Test
    void 비우면_꺼짐과_기본_모델() {
        TalkSummaryProperties properties = new TalkSummaryProperties(null, null, null);

        assertThat(properties.type()).isEqualTo(Type.NONE);
        assertThat(properties.model()).isEqualTo("claude-opus-5-5");
    }

    @Test
    void anthropic인데_키가_비면_기동_실패() {
        assertThatThrownBy(() -> new TalkSummaryProperties(Type.ANTHROPIC, null, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JG4SEASONS_LLM_API_KEY");
    }

    @Test
    void 키_공백은_지우고_toString에_없다() {
        TalkSummaryProperties properties = new TalkSummaryProperties(Type.ANTHROPIC, null, " sk-ant-x\n");

        assertThat(properties.apiKey()).isEqualTo("sk-ant-x");
        assertThat(properties.toString()).doesNotContain("sk-ant-x");
    }
}
