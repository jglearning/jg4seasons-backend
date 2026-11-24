package com.jglearning.jg4seasons.wordtest.reading;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicInvalidDataException;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.beta.messages.BetaMessage;
import java.io.InterruptedIOException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Anthropic 모델로 답안지를 옮겨 적는다(beta 메시지 엔드포인트 — 서버 측 fallbacks). 제한 시간·재시도는 클라이언트 설정(30초, SDK 재시도 0 — 워커가 30초 간격으로
 * 3회). 사진·응답 원문·키는 로그·예외 메시지에 넣지 않는다
 */
public class AnthropicAnswerSheetReader implements AnswerSheetReader, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AnthropicAnswerSheetReader.class);

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
        String stopReason;
        String text;
        String actualModel;
        long inputTokens;
        long outputTokens;
        try {
            BetaMessage message = client.beta()
                    .messages()
                    .create(ReadRequests.request(model, image, mediaType, itemCount, promptTemplate));
            // 응답 칸을 꺼낼 때도 SDK가 형식을 검증한다 — 같은 try 안에서
            stopReason = message.stopReason().map(reason -> reason.asString()).orElse("");
            text = message.content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(textBlock -> textBlock.text())
                    .collect(Collectors.joining());
            actualModel = message.model().asString();
            inputTokens = message.usage().inputTokens();
            outputTokens = message.usage().outputTokens();
        } catch (AnthropicServiceException e) {
            throw classify(
                    e.statusCode(), e.getClass().getSimpleName(), e.requestId().orElse(null));
        } catch (AnthropicIoException e) {
            throw new ReadFailure(
                    timedOut(e) ? ReadFailure.Kind.TIMEOUT : ReadFailure.Kind.API_ERROR,
                    "요청 실패: " + e.getClass().getSimpleName());
        } catch (AnthropicInvalidDataException e) {
            throw new ReadFailure(ReadFailure.Kind.MALFORMED, "응답 칸 형식이 다르다");
        } catch (AnthropicException e) {
            throw new ReadFailure(
                    ReadFailure.Kind.API_ERROR, "API 오류: " + e.getClass().getSimpleName());
        }
        // 해석(거절·잘림·형식) 전에 남긴다 — 실패한 호출도 비용·모델이 기록되게. 원문은 남기지 않음
        log.info("단어시험 읽기 응답: 모델 {} 중단 {} 토큰 입력 {} 출력 {}", actualModel, stopReason, inputTokens, outputTokens);
        return ReadRequests.parse(stopReason, text, actualModel, inputTokens, outputTokens);
    }

    /**
     * API 오류 분류. 400·413·422는 요청 거절(REJECTED), 401·403·404는 설정 오류(CONFIG) — 둘 다 다시 해도 같아 재시도하지 않는다. 그 밖(429·5xx)은
     * API_ERROR(재시도). 메시지에 상태 코드·예외 종류·request-id(지원 문의용)만
     */
    static ReadFailure classify(int status, String type, String requestId) {
        ReadFailure.Kind kind = switch (status) {
            case 400, 413, 422 -> ReadFailure.Kind.REJECTED;
            case 401, 403, 404 -> ReadFailure.Kind.CONFIG;
            default -> ReadFailure.Kind.API_ERROR;
        };
        return new ReadFailure(kind, status + " " + type + " req=" + (requestId == null ? "-" : requestId));
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
