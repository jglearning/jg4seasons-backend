package com.jglearning.jg4seasons.listening.segment;

/** 트랙 안의 청취 구간(ms, 시작 포함·끝 제외). 병합된 구간(track_progress.merged_ranges)의 한 칸. */
public record Range(int startMs, int endMs) {}
