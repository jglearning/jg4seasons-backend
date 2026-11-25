package com.jglearning.jg4seasons.talk.summary;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.beta.messages.BetaMessage;
import com.jglearning.jg4seasons.talk.TalkLevel;
import com.jglearning.jg4seasons.talk.TalkTranscript.Turn;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Anthropic 모델로 글자 기록을 요약한다(beta 메시지 엔드포인트 — 서버 측 fallbacks). 제한 시간 20초·SDK 재시도 0은 클라이언트 설정. 실패는 모두 empty(요약 실패가 종료를
 * 막지 않음 — 작업 지시 A-2). 글자 기록·응답 원문·키는 로그에 넣지 않는다(모델·중단 이유·토큰 수·실패 종류만)
 */
public class AnthropicTalkSummarizer implements TalkSummarizer {

    private static final Logger log = LoggerFactory.getLogger(AnthropicTalkSummarizer.class);

    private final AnthropicClient client;
    private final String model;
    private final String promptTemplate;

    public AnthropicTalkSummarizer(AnthropicClient client, String model, String promptTemplate) {
        this.client = client;
        this.model = model;
        this.promptTemplate = promptTemplate;
    }

    @Override
    public Optional<TalkSummary> summarize(TalkLevel level, List<Turn> turns) {
        long started = System.nanoTime();
        BetaMessage message;
        String stopReason;
        String text;
        try {
            message = client.beta().messages().create(SummaryRequests.request(model, promptTemplate, level, turns));
            // 응답 칸을 꺼낼 때도 SDK가 형식을 검증한다 — 같은 try 안에서
            stopReason = message.stopReason().map(reason -> reason.asString()).orElse("");
            text = message.content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(textBlock -> textBlock.text())
                    .collect(Collectors.joining());
        } catch (AnthropicServiceException e) {
            log.warn(
                    "프리토킹 요약 실패: HTTP {} {} req={} {}ms",
                    e.statusCode(),
                    e.getClass().getSimpleName(),
                    e.requestId().orElse("-"),
                    elapsedMs(started));
            return Optional.empty();
        } catch (AnthropicException e) {
            // 시간 초과(20초)·연결 오류·응답 칸 형식 — 종류와 원인 종류, 걸린 시간(20초 상한 판단 근거, 리뷰 10)
            log.warn(
                    "프리토킹 요약 실패: {} 원인 {} {}ms",
                    e.getClass().getSimpleName(),
                    e.getCause() == null ? "-" : e.getCause().getClass().getSimpleName(),
                    elapsedMs(started));
            return Optional.empty();
        } catch (RuntimeException e) {
            // SDK가 감싸지 않는 예외(요청 빌더 검증·HTTP 클라이언트 내부) — 계약은 "예외를 던지지 않음"(리뷰 2). 예상 밖이라 ERROR, 종류만
            log.error("프리토킹 요약 실패(예상 밖): {}", e.getClass().getName());
            return Optional.empty();
        }
        logUsage(message, stopReason, started);
        Optional<TalkSummary> summary = SummaryRequests.parse(stopReason, text);
        if (summary.isEmpty()) {
            log.warn("프리토킹 요약 실패: 응답 해석 불가(중단 {})", stopReason);
        }
        return summary;
    }

    /** 사용량 로그는 덤 — 칸을 꺼내다 실패해도 이미 받은 요약은 살린다(리뷰 10) */
    private static void logUsage(BetaMessage message, String stopReason, long started) {
        try {
            log.info(
                    "프리토킹 요약 응답: 모델 {} 중단 {} 토큰 입력 {} 출력 {} {}ms",
                    message.model().asString(),
                    stopReason,
                    message.usage().inputTokens(),
                    message.usage().outputTokens(),
                    elapsedMs(started));
        } catch (RuntimeException e) {
            log.warn("프리토킹 요약 사용량을 읽지 못했다: {}", e.getClass().getSimpleName());
        }
    }

    private static long elapsedMs(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }
}
