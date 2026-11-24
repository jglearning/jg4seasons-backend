package com.jglearning.jg4seasons.wordtest.reading;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.models.beta.messages.BetaMessage;
import java.io.InterruptedIOException;
import java.util.stream.Collectors;

/**
 * Anthropic 모델로 답안지를 옮겨 적는다(beta 메시지 엔드포인트 — 서버 측 fallbacks). 제한 시간·재시도는 클라이언트 설정(30초, SDK 재시도 0 — 워커가 30초 간격으로
 * 3회). 사진·응답 원문·키는 로그·예외 메시지에 넣지 않는다
 */
public class AnthropicAnswerSheetReader implements AnswerSheetReader, AutoCloseable {

    private final AnthropicClient client;
    private final String model;
    private final String promptTemplate;

    public AnthropicAnswerSheetReader(AnthropicClient client, String model, String promptTemplate) {
        this.client = client;
        this.model = model;
        this.promptTemplate = promptTemplate;
    }

    @Override
    public ReadResult read(byte[] image, String mediaType, int itemCount) {
        BetaMessage message;
        try {
            message = client.beta()
                    .messages()
                    .create(ReadRequests.request(model, image, mediaType, itemCount, promptTemplate));
        } catch (AnthropicIoException e) {
            throw new ReadFailure(
                    timedOut(e) ? ReadFailure.Kind.TIMEOUT : ReadFailure.Kind.API_ERROR,
                    "요청 실패: " + e.getClass().getSimpleName());
        } catch (AnthropicException e) {
            throw new ReadFailure(
                    ReadFailure.Kind.API_ERROR, "API 오류: " + e.getClass().getSimpleName());
        }
        String text = message.content().stream()
                .flatMap(block -> block.text().stream())
                .map(textBlock -> textBlock.text())
                .collect(Collectors.joining());
        return ReadRequests.parse(
                message.stopReason().map(reason -> reason.asString()).orElse(""),
                text,
                message.model().asString(),
                message.usage().inputTokens(),
                message.usage().outputTokens());
    }

    private static boolean timedOut(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof InterruptedIOException) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void close() {
        client.close();
    }
}
