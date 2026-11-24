package com.jglearning.jg4seasons.wordtest.grading;

/**
 * 한 문항의 채점 결과(저장할 모양).
 *
 * @param recognizedText 읽은 글자, 빈칸·읽을 수 없음이면 null
 * @param unsureReason 애매한 이유, 없으면 null
 */
public record GradedAnswer(long itemId, String recognizedText, AnswerResult result, UnsureReason unsureReason) {

    /** 판정 규칙은 Judgement와 같다(unclear는 이유 필수, unreadable은 unclear와만). 빈칸·unclear는 글자가 없다 */
    public GradedAnswer {
        new Judgement(result, unsureReason);
        if ((result == AnswerResult.BLANK || result == AnswerResult.UNCLEAR) && recognizedText != null) {
            throw new IllegalArgumentException(result + "에는 읽은 글자를 두지 않는다");
        }
    }
}
