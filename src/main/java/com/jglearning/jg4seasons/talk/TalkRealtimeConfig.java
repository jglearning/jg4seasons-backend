package com.jglearning.jg4seasons.talk;

import com.jglearning.jg4seasons.talk.prompt.TalkPrompts;
import com.jglearning.jg4seasons.talk.realtime.FakeRealtimeSessionIssuer;
import com.jglearning.jg4seasons.talk.realtime.IssuedSecret;
import com.jglearning.jg4seasons.talk.realtime.OpenAiRealtimeSessionIssuer;
import com.jglearning.jg4seasons.talk.realtime.RealtimeSessionIssuer;
import com.jglearning.jg4seasons.talk.realtime.RealtimeSessionRequest;
import com.jglearning.jg4seasons.talk.realtime.RealtimeUnavailableException;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
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
@EnableConfigurationProperties(TalkProperties.class)
class TalkRealtimeConfig {

    private static final Logger log = LoggerFactory.getLogger(TalkRealtimeConfig.class);

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

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

    /** 발급 꺼짐. 서버는 뜨고 세션 생성만 503 REALTIME_UNAVAILABLE */
    enum NoIssuer implements RealtimeSessionIssuer {
        INSTANCE;

        @Override
        public IssuedSecret issue(RealtimeSessionRequest request) {
            throw new RealtimeUnavailableException("임시 키 발급이 꺼져 있다(jg4seasons.talk.realtime.type)");
        }
    }
}
