package com.jglearning.jg4seasons.talk;

import java.util.Arrays;
import java.util.Optional;

/** 시작 전에 고르는 음성(계약 D-0). 실제 음성 이름은 설정값(jg4seasons.talk.realtime.voice-female·voice-male) */
public enum TalkVoice {
    FEMALE("female"),
    MALE("male");

    private final String code;

    TalkVoice(String code) {
        this.code = code;
    }

    /** 대소문자까지 정확히 같은 code만 */
    public static Optional<TalkVoice> ofCode(String code) {
        return Arrays.stream(values()).filter(voice -> voice.code.equals(code)).findFirst();
    }

    public String code() {
        return code;
    }
}
