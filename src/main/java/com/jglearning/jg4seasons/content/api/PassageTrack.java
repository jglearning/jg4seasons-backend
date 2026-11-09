package com.jglearning.jg4seasons.content.api;

/** 지문 트랙 하나와 그 회차·권. voice는 variant(p1:us_w)의 목소리 부분, durationMs는 청취율 분모. */
public record PassageTrack(
        long trackId, long lessonId, String lessonCode, long bookId, int passageNo, String voice, int durationMs) {}
