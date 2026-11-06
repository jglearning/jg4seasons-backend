package com.jglearning.jg4seasons.content.api;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jglearning.jg4seasons.content.api.LessonContent.Passage;
import com.jglearning.jg4seasons.content.api.LessonContent.Sentence;
import com.jglearning.jg4seasons.content.api.LessonContent.Speed;
import com.jglearning.jg4seasons.content.api.LessonContent.TimeRange;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 회차 내용 타입의 불변식: 시각은 문장 수와 같거나 비어 있고, 구간은 0 <= 시작 < 끝(sentence_timing CHECK와 같음). */
class LessonContentTest {

    @Test
    void 구간은_0_이상이고_시작이_끝보다_앞() {
        assertThatThrownBy(() -> new TimeRange(-1, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TimeRange(10, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TimeRange(10, 5)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 시각_개수가_문장_수와_다른_지문은_만들_수_없다() {
        List<Sentence> sentences = List.of(new Sentence(1, "One.", null), new Sentence(2, "Two.", null));
        Speed oneTiming = new Speed(1L, "1.0", "audio/x.mp3", 1000, List.of(new TimeRange(0, 500)), false);

        assertThatThrownBy(() -> new Passage(1, "P", 10, sentences, List.of(oneTiming)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
