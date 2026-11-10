package com.jglearning.jg4seasons.content.api;

/**
 * 회차 단어 하나(ADR 0027 결정 6, 0029). order = 교재 순서(1부터). source는 book(어휘책 단어·숙어) 또는 extra(어휘책에 없는 회차 어휘) — 읽을 때
 * 다른 값이면 데이터 오류. 예문 해석·음원은 없을 수 있다(exampleKo·audio·exampleAudio가 null). 지금 자료에서는 어휘책에만 있는 단어가 그렇지만, 출처와는 따로
 * 판단한다(어휘책 음원이 들어오면 book도 음원이 있다).
 */
public record LessonWord(
        long id,
        int order,
        String source,
        String english,
        String korean,
        String pos,
        String exampleEn,
        String exampleKo,
        Audio audio,
        Audio exampleAudio) {

    /** 단어·예문 트랙. fileKey는 음원 버킷 키(주소는 호출자가 만든다) */
    public record Audio(long trackId, String fileKey, int durationMs) {}
}
