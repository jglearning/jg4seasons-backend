package com.jglearning.jg4seasons.wordtest.grading;

/**
 * 한 문항에서 읽은 것(손으로 쓴 글자 그대로).
 *
 * @param text 읽은 글자, null·공백이면 빈칸
 * @param unreadable 읽을 수 없다고 표시됨
 */
public record Reading(String text, boolean unreadable) {}
