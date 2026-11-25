package com.jglearning.jg4seasons.talk.summary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.jglearning.jg4seasons.talk.TalkLevel;
import com.jglearning.jg4seasons.talk.TalkRole;
import com.jglearning.jg4seasons.talk.TalkTranscript.Turn;
import java.net.SocketTimeoutException;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 요약기는 예외를 던지지 않는다(실패는 요약 없음 — 종료를 막지 않음, #77 리뷰 2) */
class AnthropicTalkSummarizerTest {

    static final List<Turn> TURNS = List.of(
            new Turn(1, TalkRole.CHARACTER, "Hi!"),
            new Turn(2, TalkRole.STUDENT, "I go to school."),
            new Turn(3, TalkRole.STUDENT, "I play soccer."));

    AnthropicClient client = mock(AnthropicClient.class, RETURNS_DEEP_STUBS);
    AnthropicTalkSummarizer summarizer = new AnthropicTalkSummarizer(client, "m", SummaryRequests.loadPrompt());

    @Test
    void 연결_오류와_시간_초과는_요약_없음() {
        when(client.beta().messages().create(any(MessageCreateParams.class)))
                .thenThrow(new AnthropicIoException("timeout", new SocketTimeoutException("read timed out")));

        assertThat(summarizer.summarize(TalkLevel.GREEN, TURNS)).isEmpty();
    }

    @Test
    void SDK가_감싸지_않는_예외도_요약_없음() {
        when(client.beta().messages().create(any(MessageCreateParams.class)))
                .thenThrow(new IllegalStateException("unexpected"));

        assertThat(summarizer.summarize(TalkLevel.GREEN, TURNS)).isEmpty();
    }
}
