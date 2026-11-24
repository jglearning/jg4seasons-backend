package com.jglearning.jg4seasons.wordtest.grading;

import java.util.Objects;

/**
 * 한 문항의 채점 결과(저장할 모양).
 *
 * @param recognizedText 읽은 글자, 빈칸·읽을 수 없음이면 null
 * @param unsureReason 애매한 이유, 없으면 null
 */
public record GradedAnswer(long itemId, String recognizedText, AnswerResult result, UnsureReason unsureReason) {

    public GradedAnswer {
        Objects.requireNonNull(result, "판정 결과가 없다");
    }
}
