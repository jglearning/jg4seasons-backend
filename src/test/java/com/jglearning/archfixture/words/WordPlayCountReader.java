package com.jglearning.archfixture.words;

import com.jglearning.archfixture.listening.api.PlayCountQuery;

/** 정상: 다른 모듈의 공개 api만 부른다(규칙 확인용 가상의 의존 — 실제 words 설계와 무관). */
public class WordPlayCountReader {
    private final PlayCountQuery playCountQuery;

    public WordPlayCountReader(PlayCountQuery playCountQuery) {
        this.playCountQuery = playCountQuery;
    }

    public int playCount(long studentId, long trackId) {
        return playCountQuery.playCount(studentId, trackId);
    }
}
