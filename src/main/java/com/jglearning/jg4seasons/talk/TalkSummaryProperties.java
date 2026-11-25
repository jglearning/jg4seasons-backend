package com.jglearning.jg4seasons.talk;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 프리토킹 요약 설정(jg4seasons.talk.summary.*). 기본은 꺼짐(none) — 종료는 되고 summary는 늘 null. anthropic인데 키가 비면 서버가 뜨지 않는다. 키는 답안지 읽기와 같은
 * 환경 변수 JG4SEASONS_LLM_API_KEY(값은 Parameter Store, 로그·toString에 넣지 않음)
 *
 * @param type none · anthropic · fake(테스트)
 * @param model 모델 이름(기본 claude-opus-5-5, 사용자 11/25)
 */
@ConfigurationProperties("jg4seasons.talk.summary")
public record TalkSummaryProperties(Type type, String model, String apiKey) {

    public enum Type {
        NONE,
        ANTHROPIC,
        FAKE
    }

    static final String DEFAULT_MODEL = "claude-opus-5-5";

    public TalkSummaryProperties {
        type = type == null ? Type.NONE : type;
        model = model == null || model.isBlank() ? DEFAULT_MODEL : model.strip();
        apiKey = apiKey == null ? null : apiKey.strip();
        if (type == Type.ANTHROPIC && (apiKey == null || apiKey.isEmpty())) {
            throw new IllegalArgumentException("jg4seasons.talk.summary.api-key가 비었다(JG4SEASONS_LLM_API_KEY)");
        }
    }

    /** 키가 로그에 찍히지 않게 */
    @Override
    public String toString() {
        return "TalkSummaryProperties[type=" + type + ", model=" + model + "]";
    }
}
