package com.jglearning.jg4seasons.content.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.content.importer.LessonManifest;
import org.junit.jupiter.api.Test;

/** 지문 목소리 목록(ADR 0027)은 여기 한 곳. 값을 검증하는 코드는 이 목록을 본다. */
class PassageVoicesTest {

    @Test
    void 네_목소리가_트랙_순서대로_있다() {
        assertThat(PassageVoices.ALL).containsExactly("us_w", "us_m", "uk_w", "uk_m");
    }

    @Test
    void 네_목소리만_맞다() {
        for (String voice : PassageVoices.ALL) {
            assertThat(PassageVoices.isValid(voice)).as(voice).isTrue();
        }
        // 죽여야 하는 틀린 구현: 대소문자를 무시하거나 앞뒤 공백을 떼고 비교하는 것, null에서 예외를 던지는 것
        for (String wrong : new String[] {null, "", "US_W", "Us_w", " us_w", "us_w ", "us_x", "en_us_w", "1.0"}) {
            assertThat(PassageVoices.isValid(wrong)).as("[%s]", wrong).isFalse();
        }
    }

    @Test
    void 목록은_바꿀_수_없다() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> PassageVoices.ALL.add("au_w"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void 콘텐츠_매니페스트는_같은_목록을_쓴다() {
        // 죽여야 하는 틀린 구현: 매니페스트가 자기 목록을 따로 들고 있는 것(값이 갈릴 수 있다)
        assertThat(LessonManifest.VOICES).isSameAs(PassageVoices.ALL);
    }
}
