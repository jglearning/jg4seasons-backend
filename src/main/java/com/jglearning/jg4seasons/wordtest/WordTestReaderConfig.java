package com.jglearning.jg4seasons.wordtest;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.jglearning.jg4seasons.wordtest.reading.AnswerSheetReader;
import com.jglearning.jg4seasons.wordtest.reading.AnthropicAnswerSheetReader;
import com.jglearning.jg4seasons.wordtest.reading.ReadRequests;
import com.jglearning.jg4seasons.wordtest.reading.ReadResult;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 답안지 읽기 구현을 설정값으로 고른다. 테스트(fake)는 테스트 설정의 @Primary 가짜가 이 빈을 가린다 */
@Configuration
@EnableConfigurationProperties(WordTestReaderProperties.class)
class WordTestReaderConfig {

    private static final Logger log = LoggerFactory.getLogger(WordTestReaderConfig.class);

    static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    @Bean
    AnswerSheetReader answerSheetReader(WordTestReaderProperties properties) {
        return switch (properties.type()) {
            case ANTHROPIC -> {
                // 제한 시간 30초, SDK 재시도 0(워커가 30초 간격으로 3회). 키는 로그에 남기지 않는다
                AnthropicClient client = AnthropicOkHttpClient.builder()
                        .apiKey(properties.apiKey())
                        .timeout(READ_TIMEOUT)
                        .maxRetries(0)
                        .build();
                log.info("답안지 읽기: Anthropic {}", properties.model());
                yield new AnthropicAnswerSheetReader(client, properties.model(), ReadRequests.loadPrompt());
            }
            case NONE, FAKE -> {
                log.info("답안지 읽기 꺼짐 — 채점 워커가 작업을 잡지 않는다");
                yield NoReader.INSTANCE;
            }
        };
    }

    /** 읽기 꺼짐. 워커는 enabled()를 보고 작업을 잡지 않는다 */
    enum NoReader implements AnswerSheetReader {
        INSTANCE;

        @Override
        public ReadResult read(byte[] image, String mediaType, int itemCount) {
            throw new IllegalStateException("답안지 읽기가 꺼져 있다(jg4seasons.word-test.reader.type)");
        }

        @Override
        public boolean enabled() {
            return false;
        }
    }
}
