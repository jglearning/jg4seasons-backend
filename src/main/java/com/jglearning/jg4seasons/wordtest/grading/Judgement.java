package com.jglearning.jg4seasons.wordtest.grading;

import java.util.Objects;

/**
 * 한 문항의 판정. 읽을 수 없음(UNREADABLE)은 오답(WRONG)과만 짝짓는다(1단계, 기획 §5-3). READ_MISMATCH·NEAR_MISS의 짝은 2단계에서 정한다.
 *
 * @param unsureReason 애매한 이유, 없으면 null
 */
public record Judgement(AnswerResult result, UnsureReason unsureReason) {

    public Judgement {
        Objects.requireNonNull(result, "판정 결과가 없다");
        if (unsureReason == UnsureReason.UNREADABLE && result != AnswerResult.WRONG) {
            throw new IllegalArgumentException("읽을 수 없음은 오답이어야 한다: " + result);
        }
    }
}
