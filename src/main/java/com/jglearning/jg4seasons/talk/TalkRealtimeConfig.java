package com.jglearning.jg4seasons.talk;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.jglearning.jg4seasons.talk.TalkTranscript.Turn;
import com.jglearning.jg4seasons.talk.prompt.TalkPrompts;
import com.jglearning.jg4seasons.talk.realtime.FakeRealtimeSessionIssuer;
import com.jglearning.jg4seasons.talk.realtime.IssuedSecret;
import com.jglearning.jg4seasons.talk.realtime.OpenAiRealtimeSessionIssuer;
import com.jglearning.jg4seasons.talk.realtime.RealtimeSessionIssuer;
import com.jglearning.jg4seasons.talk.realtime.RealtimeSessionRequest;
import com.jglearning.jg4seasons.talk.realtime.RealtimeUnavailableException;
import com.jglearning.jg4seasons.talk.summary.AnthropicTalkSummarizer;
import com.jglearning.jg4seasons.talk.summary.FakeTalkSummarizer;
import com.jglearning.jg4seasons.talk.summary.SummaryRequests;
import com.jglearning.jg4seasons.talk.summary.TalkSummarizer;
import com.jglearning.jg4seasons.talk.summary.TalkSummary;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/** 지시문 템플릿과 임시 키 발급기. 발급기는 설정값으로 고르고, 통합 테스트는 테스트 설정의 @Primary 가짜가 이 빈을 가린다 */
@Configuration
@EnableConfigurationProperties({TalkProperties.class, TalkSummaryProperties.class})
class TalkRealtimeConfig {

    private static final Logger log = LoggerFactory.getLogger(TalkRealtimeConfig.class);

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    /** 요약 제한 시간(작업 지시 A-2). 넘으면 summary null — 학생이 결과 화면에서 기다리는 시간의 상한 */
    static final Duration SUMMARY_TIMEOUT = Duration.ofSeconds(20);

    /** 지시문 템플릿. 기동 때 한 번 읽는다 — 파일이 없거나 비면 서버가 뜨지 않는다 */
    @Bean
    TalkPrompts talkPrompts() {
        return TalkPrompts.load();
    }

    @Bean
    RealtimeSessionIssuer realtimeSessionIssuer(TalkProperties properties, JsonMapper json, Clock clock) {
        TalkProperties.Realtime realtime = properties.realtime();
        return switch (realtime.type()) {
            case OPENAI -> {
                JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(
                        HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
                requests.setReadTimeout(READ_TIMEOUT);
                log.info("프리토킹 임시 키: OpenAI {}", realtime.model());
                yield new OpenAiRealtimeSessionIssuer(
                        RestClient.builder().requestFactory(requests), json, realtime.apiKey());
            }
            case FAKE -> {
                log.info("프리토킹 임시 키: 가짜(외부 호출 없음)");
                yield new FakeRealtimeSessionIssuer(clock);
            }
            case NONE -> {
                log.info("프리토킹 임시 키 꺼짐 — 세션 생성은 503");
                yield NoIssuer.INSTANCE;
            }
        };
    }

    /** 요약기. 제한 시간 20초, SDK 재시도 0(학생이 기다리는 중 — 실패하면 summary null). 테스트(fake)는 테스트 설정의 @Primary 가짜가 가린다 */
    @Bean
    TalkSummarizer talkSummarizer(TalkSummaryProperties properties) {
        return switch (properties.type()) {
            case ANTHROPIC -> {
                AnthropicClient client = AnthropicOkHttpClient.builder()
                        .apiKey(properties.apiKey())
                        .timeout(SUMMARY_TIMEOUT)
                        .maxRetries(0)
                        .build();
                log.info("프리토킹 요약: Anthropic {}", properties.model());
                yield new AnthropicTalkSummarizer(client, properties.model(), SummaryRequests.loadPrompt());
            }
            case FAKE -> {
                log.info("프리토킹 요약: 가짜(외부 호출 없음)");
                yield new FakeTalkSummarizer();
            }
            case NONE -> {
                log.info("프리토킹 요약 꺼짐 — summary는 null");
                yield NoSummarizer.INSTANCE;
            }
        };
    }

    /** 요약 꺼짐. 종료는 되고 summary는 늘 null */
    enum NoSummarizer implements TalkSummarizer {
        INSTANCE;

        @Override
        public Optional<TalkSummary> summarize(TalkLevel level, List<Turn> turns) {
            return Optional.empty();
        }
    }

    /** 발급 꺼짐. 서버는 뜨고 세션 생성만 503 REALTIME_UNAVAILABLE */
    enum NoIssuer implements RealtimeSessionIssuer {
        INSTANCE;

        @Override
        public IssuedSecret issue(RealtimeSessionRequest request) {
            throw new RealtimeUnavailableException("임시 키 발급이 꺼져 있다(jg4seasons.talk.realtime.type)");
        }
    }
}
