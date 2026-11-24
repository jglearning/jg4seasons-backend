package com.jglearning.jg4seasons.wordtest.grading;

import java.util.List;

/**
 * 답안지 한 장의 채점 결과.
 *
 * @param answers 번호 순(불변)
 * @param score 맞은(correct) 문항 수
 */
public record Graded(List<GradedAnswer> answers, int score) {

    public Graded {
        answers = List.copyOf(answers);
        if (score < 0 || score > answers.size()) {
            throw new IllegalArgumentException("점수가 범위 밖이다: " + score);
        }
    }
}
