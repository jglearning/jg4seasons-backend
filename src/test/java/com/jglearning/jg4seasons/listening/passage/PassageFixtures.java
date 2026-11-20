package com.jglearning.jg4seasons.listening.passage;

import com.jglearning.jg4seasons.content.api.LessonContent;
import com.jglearning.jg4seasons.listening.completion.CompletionRule;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** 지문 합산 단위 테스트의 재료. 규칙 기준 값은 완료 규칙 테스트와 같은 값을 문자열로 읽는다(코드에 기준 숫자를 두지 않는다 — 여기는 테스트 픽스처). */
final class PassageFixtures {

    private PassageFixtures() {}

    /** bounds = 문장 시작·끝을 차례로(s0, e0, s1, e1, …) */
    static LessonContent.Voice voice(long trackId, String voice, int durationMs, int... bounds) {
        List<LessonContent.TimeRange> timings = new ArrayList<>();
        for (int i = 0; i < bounds.length; i += 2) {
            timings.add(new LessonContent.TimeRange(bounds[i], bounds[i + 1]));
        }
        return new LessonContent.Voice(trackId, voice, "audio/x/" + trackId + ".mp3", durationMs, timings, false);
    }

    /** 문장 수는 첫 목소리의 문장 시각 수 */
    static LessonContent.Passage passage(long id, LessonContent.Voice... voices) {
        List<LessonContent.Sentence> sentences = new ArrayList<>();
        for (int i = 1; i <= voices[0].timings().size(); i++) {
            sentences.add(new LessonContent.Sentence(i, "Sentence " + i + ".", null));
        }
        return new LessonContent.Passage(id, 1, "Made-up Passage", 100, sentences, List.of(voices));
    }

    static CompletionRule rule(Set<String> requiredVoices, boolean combine) {
        return new CompletionRule(new BigDecimal("0.900"), requiredVoices, combine);
    }
}
