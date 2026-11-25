package com.jglearning.jg4seasons.talk;

import java.util.Arrays;
import java.util.Optional;

/** 주제 카드(기획 §2, 계약 D-1). code는 요청·DB 값, title은 화면 문구(화면 시안 11/26 — 프론트가 그대로 보임) */
public enum TalkTopic {
    LESSON("lesson", "오늘 배운 단어로"),
    DAY("day", "오늘 하루"),
    LIKES("likes", "좋아하는 것");

    private final String code;
    private final String title;

    TalkTopic(String code, String title) {
        this.code = code;
        this.title = title;
    }

    /** 대소문자까지 정확히 같은 code만 */
    public static Optional<TalkTopic> ofCode(String code) {
        return Arrays.stream(values()).filter(topic -> topic.code.equals(code)).findFirst();
    }

    public String code() {
        return code;
    }

    public String title() {
        return title;
    }
}
