package com.jglearning.jg4seasons.wordtest.reading;

import com.jglearning.jg4seasons.wordtest.grading.Reading;
import java.util.List;
import java.util.Objects;

/**
 * LLM이 옮겨 적은 결과를 판정에 넘기기 전에 검증한다. 번호가 1~N에 정확히 한 번씩이 아니면 실패(다른 쪽을 찍었거나 번호가 밀림 — 한 번 밀리면 그 쪽 전체가 틀리므로 받지
 * 않는다). 칸 길이(답안 칸 100자)를 넘는 글자와 읽을 수 없음은 글자 없이 unreadable로
 */
public final class ReadValidation {

    /** word_test_answer.recognized_text 길이 */
    static final int MAX_TEXT = 100;

    private ReadValidation() {}

    /** 번호 순 Reading 목록. 위반이면 ReadFailure(COUNT_MISMATCH·MALFORMED) */
    public static List<Reading> check(ReadResult result, int itemCount) {
        if (result == null || result.items() == null || result.items().stream().anyMatch(Objects::isNull)) {
            throw new ReadFailure(ReadFailure.Kind.MALFORMED, "items가 없거나 빈 항목이 있다");
        }
        if (result.items().size() != itemCount) {
            throw new ReadFailure(
                    ReadFailure.Kind.COUNT_MISMATCH,
                    "문항 " + itemCount + "개인데 " + result.items().size() + "개를 읽음");
        }
        Reading[] byNo = new Reading[itemCount];
        for (ReadItem item : result.items()) {
            if (item.no() < 1 || item.no() > itemCount || byNo[item.no() - 1] != null) {
                throw new ReadFailure(ReadFailure.Kind.COUNT_MISMATCH, "문항 번호가 1~" + itemCount + "에 한 번씩이 아님");
            }
            byNo[item.no() - 1] = reading(item);
        }
        return List.of(byNo);
    }

    private static Reading reading(ReadItem item) {
        String text = item.text();
        if (item.unreadable() || (text != null && text.codePointCount(0, text.length()) > MAX_TEXT)) {
            return new Reading(null, true);
        }
        return new Reading(text, false);
    }
}
