package com.jglearning.jg4seasons.wordtest.reading;

/**
 * LLM이 옮겨 적은 한 칸(검증 전 그대로).
 *
 * @param no 문항 번호(책의 번호)
 * @param text 손으로 쓴 글자 그대로, 빈칸이면 "" 또는 null
 * @param unreadable 읽을 수 없다고 표시
 */
public record ReadItem(int no, String text, boolean unreadable) {}
