package com.jglearning.jg4seasons.talk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jglearning.jg4seasons.talk.TalkProperties.Realtime;
import com.jglearning.jg4seasons.talk.TalkProperties.Realtime.Type;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TalkPropertiesTest {

    @Test
    void 비우면_기본값() {
        TalkProperties properties = new TalkProperties(null, null);

        assertThat(properties.maxSeconds()).isEqualTo(180);
        Realtime realtime = properties.realtime();
        assertThat(realtime.type()).isEqualTo(Type.NONE);
        assertThat(realtime.model()).isEqualTo("gpt-realtime-2.1-mini");
        assertThat(realtime.voiceFor(TalkVoice.FEMALE)).isEqualTo("marin");
        assertThat(realtime.voiceFor(TalkVoice.MALE)).isEqualTo("cedar");
        assertThat(realtime.transcriptionModel()).isEqualTo("gpt-4o-mini-transcribe");
        assertThat(realtime.keyTtlSeconds()).isEqualTo(60);
    }

    @Test
    void openai인데_키가_비면_기동_실패() {
        assertThatThrownBy(() -> new Realtime(Type.OPENAI, " ", null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JG4SEASONS_OPENAI_API_KEY");
    }

    @ParameterizedTest
    @ValueSource(ints = {9, 7201})
    void 키_수명이_문서_범위_밖이면_기동_실패(int seconds) {
        assertThatThrownBy(() -> new Realtime(Type.FAKE, null, null, null, null, null, seconds))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void 시간_제한이_0_이하면_기동_실패(int seconds) {
        assertThatThrownBy(() -> new TalkProperties(null, seconds)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toString에_키가_없다() {
        Realtime realtime = new Realtime(Type.OPENAI, "sk-secret", null, null, null, null, null);

        assertThat(realtime.toString()).doesNotContain("sk-secret");
        assertThat(new TalkProperties(realtime, null).toString()).doesNotContain("sk-secret");
    }
}
