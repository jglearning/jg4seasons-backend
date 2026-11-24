package com.jglearning.jg4seasons.wordtest.reading;

/**
 * 답안지 사진에서 문항 번호별로 손으로 쓴 글자를 옮겨 적는다(채점하지 않음 — 판정은 SpellingJudge). 실패는 {@link ReadFailure}. 구현은
 * {@code jg4seasons.word-test.reader.type}으로 고른다: none(꺼짐 — 워커가 작업을 잡지 않음) · anthropic · 테스트 가짜
 */
public interface AnswerSheetReader {

    /**
     * @param mediaType image/jpeg · image/png
     * @param itemCount 그 회차·영역의 문항 수(번호 1~itemCount)
     */
    ReadResult read(byte[] image, String mediaType, int itemCount);

    /** false면 워커가 작업을 잡지 않는다(읽기 꺼짐 — 제출은 pending으로 쌓임) */
    default boolean enabled() {
        return true;
    }
}
