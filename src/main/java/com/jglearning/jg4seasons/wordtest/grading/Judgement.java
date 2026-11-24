package com.jglearning.jg4seasons.wordtest.grading;

import java.util.Objects;

/**
 * 한 문항의 판정. 읽을 수 없음(UNREADABLE)은 판정 못 함(UNCLEAR)과만 짝짓고, UNCLEAR는 이유가 있어야 한다(기획 §5-3, 11/25 결정). READ_MISMATCH·NEAR_MISS의 짝은 2단계에서 정한다.
 *
 * @param unsureReason 애매한 이유, 없으면 null
 */
public record Judgement(AnswerResult result, UnsureReason unsureReason) {

    public Judgement {
        Objects.requireNonNull(result, "판정 결과가 없다");
        if (unsureReason == UnsureReason.UNREADABLE && result != AnswerResult.UNCLEAR) {
            throw new IllegalArgumentException("읽을 수 없음은 unclear여야 한다: " + result);
        }
        if (result == AnswerResult.UNCLEAR && unsureReason == null) {
            throw new IllegalArgumentException("unclear에는 이유가 있어야 한다");
        }
    }
}
