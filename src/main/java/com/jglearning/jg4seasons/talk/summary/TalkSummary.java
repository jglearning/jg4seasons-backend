package com.jglearning.jg4seasons.talk.summary;

import java.util.List;

/**
 * 대화가 끝난 뒤 요약(계약 D-3 summary). 화면 "잘 쓴 표현"·"고쳐볼 문장"
 *
 * @param goodExpressions 학생이 잘 쓴 표현(0~2개)
 * @param corrections 고쳐볼 문장(0~3개, said는 학생이 실제로 말한 문장)
 */
public record TalkSummary(List<String> goodExpressions, List<Correction> corrections) {

    public TalkSummary {
        goodExpressions = List.copyOf(goodExpressions);
        corrections = List.copyOf(corrections);
    }

    /**
     * @param said 학생이 말한 문장 그대로
     * @param better 더 자연스러운 문장
     * @param note 한국어 해요체 한 줄 설명
     */
    public record Correction(String said, String better, String note) {}
}
