package com.jglearning.jg4seasons.content.api;

import java.util.List;

/**
 * 지문 음원의 목소리(ADR 0027): 미국 여·미국 남·영국 여·영국 남. 목소리 값을 검증하는 코드는 이 목록을 본다 — 같은 값을 여러 곳에 따로 적지 않는다.
 *
 * <p>아직 따로 적힌 곳: 완료 규칙의 목소리 형식(listening.completion.CompletionRule의 정규식)과 응답 명세의 허용 값(애너테이션이라 목록을 못 쓴다).
 * 목소리를 바꾸면 여기와 함께 고친다.
 */
public final class PassageVoices {

    /** 트랙 순서의 기본값이기도 하다 */
    public static final List<String> ALL = List.of("us_w", "us_m", "uk_w", "uk_m");

    private PassageVoices() {}

    /** 네 목소리 중 하나인가. 글자 그대로 비교한다 — null, 대소문자가 다른 값, 앞뒤 공백이 붙은 값은 아니다 */
    public static boolean isValid(String voice) {
        return voice != null && ALL.contains(voice);
    }
}
