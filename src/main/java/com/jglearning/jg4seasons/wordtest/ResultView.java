package com.jglearning.jg4seasons.wordtest;

import com.jglearning.jg4seasons.wordtest.grading.AnswerResult;
import com.jglearning.jg4seasons.wordtest.grading.UnsureReason;

/**
 * 응답에 보이는 문항 결과. DB는 판정 그대로(읽을 수 없음 = wrong + unsure_reason unreadable)이고, 응답에서만 {@code unclear}(판단 못 함 — FE #171 제안, 사용자
 * 11/24 승인)로 보인다. 점수는 correct만 세므로 unclear는 score 밖·total 안
 */
final class ResultView {

    static final String UNCLEAR = "unclear";

    private ResultView() {}

    static String of(AnswerResult result, UnsureReason reason) {
        return reason == UnsureReason.UNREADABLE ? UNCLEAR : result.dbValue();
    }

    /** 읽은 글자, 없으면 "" */
    static String written(String recognized) {
        return recognized == null ? "" : recognized;
    }
}
