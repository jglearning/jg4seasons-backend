package com.jglearning.jg4seasons.wordtest.grading;

import java.util.List;

/**
 * 한 문항의 비교 대상. B·C·D는 문항 표의 정답·허용 답·인쇄된 첫 글자로 만든다. 비교 대상을 정하는 곳은 이것을 만드는 쪽 한 곳 — A(뜻 쓰기)는 나중에 뜻으로 만든 AnswerKey를
 * 넘긴다.
 *
 * @param hint 인쇄된 첫 글자(a, p... t...), 없으면 null
 */
public record AnswerKey(String answer, List<String> accepted, String hint) {

    public AnswerKey {
        if (answer == null || answer.isBlank()) {
            throw new IllegalArgumentException("정답이 비었다");
        }
        accepted = accepted == null ? List.of() : List.copyOf(accepted);
    }
}
