package com.jglearning.archfixture.content.api;

import com.jglearning.archfixture.listening.api.PlayCountQuery;

/** 위반: content가 listening에 의존한다(api라도 금지). */
public class LessonView {
    private final PlayCountQuery playCountQuery;

    public LessonView(PlayCountQuery playCountQuery) {
        this.playCountQuery = playCountQuery;
    }

    public int playCount(long studentId, long trackId) {
        return playCountQuery.playCount(studentId, trackId);
    }
}
