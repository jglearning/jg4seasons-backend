package com.jglearning.jg4seasons.talk;

import java.util.Arrays;
import java.util.Optional;

/** 글자 기록 한 줄의 말한 쪽(계약 D-0, V15 talk_turn CHECK) */
public enum TalkRole {
    STUDENT("student"),
    CHARACTER("character");

    private final String code;

    TalkRole(String code) {
        this.code = code;
    }

    /** 대소문자까지 정확히 같은 code만 */
    public static Optional<TalkRole> ofCode(String code) {
        return Arrays.stream(values()).filter(role -> role.code.equals(code)).findFirst();
    }

    public String code() {
        return code;
    }
}
