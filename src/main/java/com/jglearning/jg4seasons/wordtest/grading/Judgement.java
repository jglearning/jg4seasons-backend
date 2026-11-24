package com.jglearning.jg4seasons.wordtest.grading;

/**
 * 한 문항의 판정.
 *
 * @param unsureReason 애매한 이유, 없으면 null
 */
public record Judgement(AnswerResult result, UnsureReason unsureReason) {}
