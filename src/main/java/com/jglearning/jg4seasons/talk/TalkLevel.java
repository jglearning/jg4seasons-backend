package com.jglearning.jg4seasons.talk;

import java.util.Arrays;

/**
 * 프리토킹 레벨. 반의 레벨 코드(level.code)를 응답·프롬프트 파일 이름에 쓰는 slug로 바꾼다(기획 §4-2 {@code level-green.txt}). DB(talk_session.level_code)에는
 * 레벨 코드를 그대로 저장한다
 */
public enum TalkLevel {
    MINT("L1", "mint", TalkVoice.FEMALE),
    GREEN("L2", "green", TalkVoice.FEMALE),
    BLUE("L3", "blue", TalkVoice.FEMALE),
    NAVY("L4", "navy", TalkVoice.MALE),
    BLACK("K", "black", TalkVoice.FEMALE);

    private final String levelCode;
    private final String slug;
    private final TalkVoice voice;

    TalkLevel(String levelCode, String slug, TalkVoice voice) {
        this.levelCode = levelCode;
        this.slug = slug;
        this.voice = voice;
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

    /** 그 레벨 캐릭터의 목소리 성별(사용자 11/25: 네이비만 남성). 학생은 고르지 않는다(#86). 실제 목소리 이름은 설정값 */
    public TalkVoice voice() {
        return voice;
    }
}
