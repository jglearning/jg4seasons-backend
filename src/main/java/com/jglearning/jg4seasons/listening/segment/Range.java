package com.jglearning.jg4seasons.listening.segment;

/**
 * 트랙 안의 청취 구간(ms, 시작 포함·끝 제외). 병합된 구간(track_progress.merged_ranges)의 한 칸. 0 <= 시작 < 끝(listening_segment CHECK와
 * 같음) — 어기는 값은 만들 수 없다. 받은 세그먼트를 버릴지(dropped) 정할 때는 만들기 전에 isValid로 본다.
 */
public record Range(int startMs, int endMs) {

    public Range {
        if (!isValid(startMs, endMs)) {
            throw new IllegalArgumentException("구간은 0 <= 시작 < 끝이어야 한다: " + startMs + "~" + endMs);
        }
    }

    public static boolean isValid(int startMs, int endMs) {
        return startMs >= 0 && endMs > startMs;
    }
}
