package com.jglearning.jg4seasons.wordtest.grading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jglearning.jg4seasons.content.api.WordTestItem;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 채점 조립: 문항(정답·허용 답·첫 글자)과 읽은 글자를 SpellingJudge로 판정, 점수 = 맞은 수. 문항은 지어낸 것 */
class GradingTest {

    static final List<WordTestItem> ITEMS = List.of(
            new WordTestItem(11, 1, "아름다운", "beautiful", null, List.of()),
            new WordTestItem(12, 2, "분위기", "atmosphere", "a", List.of()),
            new WordTestItem(13, 3, "판단", "judgment", null, List.of("judgement")),
            new WordTestItem(14, 4, "받다", "receive", null, List.of()),
            new WordTestItem(15, 5, "도서관", "library", null, List.of()));

    @Test
    void 문항마다_판정하고_점수는_맞은_수() {
        Graded graded = Grading.grade(
                ITEMS,
                List.of(
                        new Reading("beautful", false), // 틀림
                        new Reading("tmosphere", false), // 첫 글자 뒤에 이어 씀 → 맞음
                        new Reading("Judgement", false), // 허용 답 → 맞음
                        new Reading("", false), // 빈칸
                        new Reading(null, true))); // 읽을 수 없음

        assertThat(graded.score()).isEqualTo(2);
        assertThat(graded.answers())
                .containsExactly(
                        new GradedAnswer(11, "beautful", AnswerResult.WRONG, null),
                        new GradedAnswer(12, "tmosphere", AnswerResult.CORRECT, null),
                        new GradedAnswer(13, "Judgement", AnswerResult.CORRECT, null),
                        new GradedAnswer(14, null, AnswerResult.BLANK, null),
                        new GradedAnswer(15, null, AnswerResult.WRONG, UnsureReason.UNREADABLE));
    }

    @Test
    void 빈칸은_공백만_써도_저장_글자_없이() {
        Graded graded = Grading.grade(ITEMS.subList(0, 1), List.of(new Reading("   ", false)));

        assertThat(graded.answers()).containsExactly(new GradedAnswer(11, null, AnswerResult.BLANK, null));
        assertThat(graded.score()).isZero();
    }

    @Test
    void 문항과_읽은_수가_다르면_거부한다() {
        assertThatThrownBy(() -> Grading.grade(ITEMS, List.of(new Reading("a", false))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
