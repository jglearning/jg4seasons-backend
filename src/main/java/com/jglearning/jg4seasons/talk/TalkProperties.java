package com.jglearning.jg4seasons.talk;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 프리토킹 설정(jg4seasons.talk.*). 모델·음성 이름은 서비스 쪽 가격 변경·모델 종료에 대비해 설정값으로 둔다(기획 §6).
 *
 * @param maxSeconds 한 세션의 시간 제한(초, 기본 180). 브라우저가 이 시간에 마무리 신호를 보내고 연결을 끊는다
 */
@ConfigurationProperties("jg4seasons.talk")
public record TalkProperties(Realtime realtime, Integer maxSeconds) {

    static final int DEFAULT_MAX_SECONDS = 180;

    public TalkProperties {
        realtime = realtime == null ? new Realtime(null, null, null, null, null, null, null) : realtime;
        maxSeconds = maxSeconds == null ? DEFAULT_MAX_SECONDS : maxSeconds;
        if (maxSeconds <= 0) {
            throw new IllegalArgumentException("jg4seasons.talk.max-seconds는 1 이상: " + maxSeconds);
        }
    }

    /**
     * 임시 키 발급(jg4seasons.talk.realtime.*). 기본은 꺼짐(none) — 키 없이 배포해도 서버는 뜨고 세션 생성만 503. openai인데 키가 비면 서버가 뜨지 않는다. 키는
     * 환경 변수 JG4SEASONS_OPENAI_API_KEY로만(값은 Parameter Store, 로그·toString에 넣지 않음)
     *
     * @param type none · openai · fake(로컬·테스트, 고정 값)
     * @param keyTtlSeconds 임시 키 수명(문서 범위 10~7200초). 키는 연결을 시작할 때만 쓰고, 시작한 세션은 만료 뒤에도 이어진다
     */
    public record Realtime(
            Type type,
            String apiKey,
            String model,
            String voiceFemale,
            String voiceMale,
            String transcriptionModel,
            Integer keyTtlSeconds) {

        public enum Type {
            NONE,
            OPENAI,
            FAKE
        }

        static final String DEFAULT_MODEL = "gpt-realtime-2.1-mini";
        static final String DEFAULT_VOICE_FEMALE = "marin";
        static final String DEFAULT_VOICE_MALE = "cedar";
        static final String DEFAULT_TRANSCRIPTION_MODEL = "gpt-4o-mini-transcribe";
        static final int DEFAULT_KEY_TTL_SECONDS = 60;

        public Realtime {
            type = type == null ? Type.NONE : type;
            // Parameter Store 값 끝의 줄바꿈·공백이 Authorization 헤더에 들어가지 않게(리뷰 9)
            apiKey = apiKey == null ? null : apiKey.strip();
            model = orDefault(model, DEFAULT_MODEL);
            voiceFemale = orDefault(voiceFemale, DEFAULT_VOICE_FEMALE);
            voiceMale = orDefault(voiceMale, DEFAULT_VOICE_MALE);
            transcriptionModel = orDefault(transcriptionModel, DEFAULT_TRANSCRIPTION_MODEL);
            keyTtlSeconds = keyTtlSeconds == null ? DEFAULT_KEY_TTL_SECONDS : keyTtlSeconds;
            if (keyTtlSeconds < 10 || keyTtlSeconds > 7200) {
                throw new IllegalArgumentException(
                        "jg4seasons.talk.realtime.key-ttl-seconds는 10~7200: " + keyTtlSeconds);
            }
            if (type == Type.OPENAI && (apiKey == null || apiKey.isBlank())) {
                throw new IllegalArgumentException("jg4seasons.talk.realtime.api-key가 비었다(JG4SEASONS_OPENAI_API_KEY)");
            }
        }

        public String voiceFor(TalkVoice voice) {
            return voice == TalkVoice.FEMALE ? voiceFemale : voiceMale;
        }

        private static String orDefault(String value, String fallback) {
            return value == null || value.isBlank() ? fallback : value.strip();
        }

        /** 키가 로그에 찍히지 않게 */
        @Override
        public String toString() {
            return "Realtime[type=" + type + ", model=" + model + ", voiceFemale=" + voiceFemale + ", voiceMale="
                    + voiceMale + ", transcriptionModel=" + transcriptionModel + ", keyTtlSeconds=" + keyTtlSeconds
                    + "]";
        }
    }
}
