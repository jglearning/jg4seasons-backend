package com.jglearning.jg4seasons.talk;

import java.util.Arrays;
import java.util.Optional;

/** 대화가 끝난 이유(계약 D-3, V16 CHECK): 끝내기 · 시간 종료 · 화면 이탈 · 연결 끊김 */
public enum TalkEndReason {
    USER("user"),
    TIMEOUT("timeout"),
    LEFT("left"),
    DISCONNECTED("disconnected");

    private final String code;

    TalkEndReason(String code) {
        this.code = code;
    }

    /** 대소문자까지 정확히 같은 code만 */
    public static Optional<TalkEndReason> ofCode(String code) {
        return Arrays.stream(values())
                .filter(reason -> reason.code.equals(code))
                .findFirst();
    }

    public String code() {
        return code;
    }
}
