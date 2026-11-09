package com.jglearning.jg4seasons.content.importer;

import com.jglearning.jg4seasons.content.api.LessonContent.TimeRange;
import java.util.ArrayList;
import java.util.List;

/**
 * 문장 파일 길이와 지문 음원 길이로 문장 시각을 계산한다(ADR 0027).
 *
 * <p>지문 음원은 문장 파일을 같은 간격으로 이어 붙인 것으로 본다. 간격 = (지문 길이 − 문장 길이 합) ÷ (문장 수 − 1), 문장 i의 시작 = 앞 문장 길이 합 + i × 간격.
 * 반올림 오차가 쌓이지 않게 누적값을 실수로 계산하고, 마지막 문장의 끝은 지문 길이로 맞춘다.
 */
public final class PassageTimings {

    /** 녹음 자료에서 잰 간격은 약 0.8초다. 이보다 훨씬 크면 문장 파일과 지문 음원이 맞지 않는 것으로 본다 */
    static final int MAX_GAP_MS = 3000;

    private PassageTimings() {}

    public static List<TimeRange> of(List<Integer> sentenceMs, int passageMs) {
        if (sentenceMs.isEmpty()) {
            throw new IllegalArgumentException("문장이 없다");
        }
        long sum = 0;
        for (int ms : sentenceMs) {
            if (ms <= 0) {
                throw new IllegalArgumentException("문장 길이는 0보다 커야 한다: " + ms);
            }
            sum += ms;
        }
        int n = sentenceMs.size();
        if (n == 1) {
            if (sum != passageMs) {
                throw new IllegalArgumentException("문장이 하나면 문장 길이와 지문 길이가 같아야 한다: " + sum + " != " + passageMs);
            }
            return List.of(new TimeRange(0, passageMs));
        }
        double gap = (double) (passageMs - sum) / (n - 1);
        if (gap < 0 || gap > MAX_GAP_MS) {
            throw new IllegalArgumentException("문장 사이 간격이 0~" + MAX_GAP_MS + "ms 밖이다: " + gap);
        }
        List<TimeRange> timings = new ArrayList<>(n);
        long before = 0;
        for (int i = 0; i < n; i++) {
            int start = (int) Math.round(before + i * gap);
            before += sentenceMs.get(i);
            int end = i == n - 1 ? passageMs : (int) Math.round(before + i * gap);
            timings.add(new TimeRange(start, end));
        }
        return List.copyOf(timings);
    }
}
