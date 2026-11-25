package com.jglearning.jg4seasons.talk.summary;

import com.jglearning.jg4seasons.talk.TalkLevel;
import com.jglearning.jg4seasons.talk.TalkTranscript.Turn;
import java.util.List;
import java.util.Optional;

/**
 * 글자 기록 요약. 구현은 설정(jg4seasons.talk.summary.type)으로 고른다: anthropic(실제) · fake(테스트) · none(꺼짐 — 늘 empty). 요약 실패가 종료를 막지
 * 않도록 실패(거절·잘림·형식 다름·시간 초과·API 오류)는 예외 대신 empty. 결과는 부르는 쪽이 SummaryGuard로 다시 거른다
 */
public interface TalkSummarizer {

    Optional<TalkSummary> summarize(TalkLevel level, List<Turn> turns);
}
