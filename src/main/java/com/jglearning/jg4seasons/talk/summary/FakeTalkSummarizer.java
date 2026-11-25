package com.jglearning.jg4seasons.talk.summary;

import com.jglearning.jg4seasons.talk.TalkLevel;
import com.jglearning.jg4seasons.talk.TalkRole;
import com.jglearning.jg4seasons.talk.TalkTranscript.Turn;
import com.jglearning.jg4seasons.talk.summary.TalkSummary.Correction;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 외부 호출 없는 요약기(통합 테스트). 학생 첫 줄을 고쳐볼 문장으로, "play soccer"를 잘 쓴 표현으로 돌려준다 — SummaryGuard가 그대로 통과시키는 모양. 테스트는
 * failNext로 실패를, calls·lastTurns로 부른 횟수와 받은 글자 기록을 본다
 */
public class FakeTalkSummarizer implements TalkSummarizer {

    private final AtomicBoolean failNext = new AtomicBoolean();
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicReference<List<Turn>> lastTurns = new AtomicReference<>();

    @Override
    public Optional<TalkSummary> summarize(TalkLevel level, List<Turn> turns) {
        calls.incrementAndGet();
        lastTurns.set(turns);
        if (failNext.getAndSet(false)) {
            return Optional.empty();
        }
        List<Correction> corrections = turns.stream()
                .filter(turn -> turn.role() == TalkRole.STUDENT)
                .findFirst()
                .map(turn -> List.of(new Correction(turn.text(), turn.text() + " (better)", "이렇게 말해 봐요")))
                .orElse(List.of());
        return Optional.of(new TalkSummary(List.of("play soccer"), corrections));
    }

    public void failNext() {
        failNext.set(true);
    }

    public int calls() {
        return calls.get();
    }

    public List<Turn> lastTurns() {
        return lastTurns.get();
    }

    public void reset() {
        failNext.set(false);
        calls.set(0);
        lastTurns.set(null);
    }
}
