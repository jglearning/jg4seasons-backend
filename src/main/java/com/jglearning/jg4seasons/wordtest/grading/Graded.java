package com.jglearning.jg4seasons.wordtest.grading;

import java.util.List;

/**
 * 답안지 한 장의 채점 결과.
 *
 * @param answers 번호 순(불변)
 */
public record Graded(List<GradedAnswer> answers) {

    public Graded {
        answers = List.copyOf(answers);
    }

    /** 맞은(correct) 문항 수 — 답안에서 계산(unclear·빈칸은 세지 않음) */
    public int score() {
        return (int) answers.stream()
                .filter(answer -> answer.result() == AnswerResult.CORRECT)
                .count();
    }
}
