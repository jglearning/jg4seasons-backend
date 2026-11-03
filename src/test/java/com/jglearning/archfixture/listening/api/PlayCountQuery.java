package com.jglearning.archfixture.listening.api;

/** listening 모듈 공개 api. */
public interface PlayCountQuery {
    int playCount(long studentId, long trackId);
}
