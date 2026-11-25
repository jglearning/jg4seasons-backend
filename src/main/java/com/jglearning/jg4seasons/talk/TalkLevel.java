package com.jglearning.jg4seasons.talk;

import java.util.Arrays;

/**
 * 프리토킹 레벨. 반의 레벨 코드(level.code)를 응답·프롬프트 파일 이름에 쓰는 slug로 바꾼다(기획 §4-2 {@code level-green.txt}). DB(talk_session.level_code)에는
 * 레벨 코드를 그대로 저장한다
 */
public enum TalkLevel {
    MINT("L1", "mint"),
    GREEN("L2", "green"),
    BLUE("L3", "blue"),
    NAVY("L4", "navy"),
    BLACK("K", "black");

    private final String levelCode;
    private final String slug;

    TalkLevel(String levelCode, String slug) {
        this.levelCode = levelCode;
        this.slug = slug;
    }

    /** 모르는 코드는 레벨 표와 이 enum이 어긋난 것 — 데이터 오류 */
    public static TalkLevel ofLevelCode(String levelCode) {
        return Arrays.stream(values())
                .filter(level -> level.levelCode.equals(levelCode))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("프리토킹에 없는 레벨 코드: " + levelCode));
    }

    public String levelCode() {
        return levelCode;
    }

    public String slug() {
        return slug;
    }
}
