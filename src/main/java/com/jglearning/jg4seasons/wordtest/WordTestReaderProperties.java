package com.jglearning.jg4seasons.wordtest;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 답안지 읽기 설정(jg4seasons.word-test.reader.*). 기본은 꺼짐(none) — 키 없이 배포해도 뜨고, 제출은 pending으로 쌓인다. anthropic인데 키가 비면 서버가 뜨지 않는다.
 * 키는 환경 변수 JG4SEASONS_LLM_API_KEY로만(오류 메시지·로그에 넣지 않음)
 *
 * @param type none · anthropic · fake(테스트)
 * @param model 모델 이름(기본 Sonnet 5.5)
 */
@ConfigurationProperties("jg4seasons.word-test.reader")
public record WordTestReaderProperties(Type type, String model, String apiKey) {

    public enum Type {
        NONE,
        ANTHROPIC,
        FAKE
    }

    static final String DEFAULT_MODEL = "claude-sonnet-5-5";

    public WordTestReaderProperties {
        type = type == null ? Type.NONE : type;
        model = model == null || model.isBlank() ? DEFAULT_MODEL : model.strip();
        if (type == Type.ANTHROPIC && (apiKey == null || apiKey.isBlank())) {
            throw new IllegalArgumentException("jg4seasons.word-test.reader.api-key가 비었다(JG4SEASONS_LLM_API_KEY)");
        }
    }

    /** 키가 로그에 찍히지 않게 */
    @Override
    public String toString() {
        return "WordTestReaderProperties[type=" + type + ", model=" + model + "]";
    }
}
