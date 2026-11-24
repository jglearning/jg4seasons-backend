package com.jglearning.jg4seasons.wordtest;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.wordtest.grading.AnswerResult;
import com.jglearning.jg4seasons.wordtest.grading.UnsureReason;
import org.junit.jupiter.api.Test;

/**
 * 응답의 문항 결과(FE #171 제안, 사용자 11/24 승인 — 정할 것 1 (A)): DB는 wrong + unreadable 그대로, 응답에서만 unclear. 2단계 사유(near_miss 등)는 결과 그대로
 */
class ResultViewTest {

    @Test
    void 결과는_소문자_그대로이고_읽을_수_없음만_unclear() {
        assertThat(ResultView.of(AnswerResult.CORRECT, null)).isEqualTo("correct");
        assertThat(ResultView.of(AnswerResult.WRONG, null)).isEqualTo("wrong");
        assertThat(ResultView.of(AnswerResult.BLANK, null)).isEqualTo("blank");
        assertThat(ResultView.of(AnswerResult.WRONG, UnsureReason.UNREADABLE)).isEqualTo("unclear");
        assertThat(ResultView.of(AnswerResult.WRONG, UnsureReason.NEAR_MISS)).isEqualTo("wrong");
        assertThat(ResultView.of(AnswerResult.CORRECT, UnsureReason.READ_MISMATCH))
                .isEqualTo("correct");
    }

    @Test
    void 쓴_글자가_없으면_빈_문자열() {
        assertThat(ResultView.written(null)).isEmpty();
        assertThat(ResultView.written("beautful")).isEqualTo("beautful");
    }
}
