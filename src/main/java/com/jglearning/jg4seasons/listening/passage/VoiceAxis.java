package com.jglearning.jg4seasons.listening.passage;

import com.jglearning.jg4seasons.content.api.LessonContent;
import com.jglearning.jg4seasons.listening.segment.Range;
import java.util.List;

/**
 * 지문 음원 하나(목소리 하나)의 시간축: 길이와 문장 구간(ms). sentences가 비면 문장 시각이 없다는 뜻이고 음원 전체를 조각 하나로 본다.
 *
 * <p>문장은 순서대로이고 겹치지 않으며 길이 안에 있다. 문장 사이가 붙어 있어도(간격 0) 된다. 어기는 값은 만들 수 없다.
 */
public record VoiceAxis(int durationMs, List<Range> sentences) {

    public VoiceAxis {
        if (durationMs <= 0) {
            throw new IllegalArgumentException("음원 길이는 0보다 커야 한다: " + durationMs);
        }
        sentences = List.copyOf(sentences);
        int previousEnd = 0;
        for (Range sentence : sentences) {
            if (sentence.startMs() < previousEnd || sentence.endMs() > durationMs) {
                throw new IllegalArgumentException(
                        "문장 구간은 순서대로이고 겹치지 않으며 길이(" + durationMs + ") 안이어야 한다: " + sentences);
            }
            previousEnd = sentence.endMs();
        }
    }

    public static VoiceAxis of(LessonContent.Voice voice) {
        return new VoiceAxis(
                voice.durationMs(),
                voice.timings().stream()
                        .map(timing -> new Range(timing.startMs(), timing.endMs()))
                        .toList());
    }
}
