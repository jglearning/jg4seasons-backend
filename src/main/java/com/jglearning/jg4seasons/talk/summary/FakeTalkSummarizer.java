package com.jglearning.jg4seasons.talk.summary;

import com.jglearning.jg4seasons.talk.TalkLevel;
import com.jglearning.jg4seasons.talk.TalkRole;
import com.jglearning.jg4seasons.talk.TalkTranscript.Turn;
import com.jglearning.jg4seasons.talk.summary.TalkSummary.Correction;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 외부 호출 없는 요약기(통합 테스트). 학생 첫 줄을 고쳐볼 문장으로, "play soccer"를 잘 쓴 표현으로 돌려준다 — SummaryGuard가 그대로 통과시키는 모양. 테스트는
 * failNext로 실패를, calls·lastTurns로 부른 횟수와 받은 글자 기록을 본다
 */
public class FakeTalkSummarizer implements TalkSummarizer {

    private final AtomicBoolean failNext = new AtomicBoolean();
    private final AtomicBoolean throwNext = new AtomicBoolean();
    private final AtomicReference<CountDownLatch[]> blockNext = new AtomicReference<>();
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicReference<List<Turn>> lastTurns = new AtomicReference<>();

    @Override
    public Optional<TalkSummary> summarize(TalkLevel level, List<Turn> turns) {
        calls.incrementAndGet();
        lastTurns.set(turns);
        CountDownLatch[] block = blockNext.getAndSet(null);
        if (block != null) {
            block[0].countDown();
            try {
                block[1].await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (throwNext.getAndSet(false)) {
            // 계약(예외 없음)을 어기는 구현·요약 뒤 저장 실패를 흉내 — 종료 응답이 500이 되지 않는지(#77 리뷰 2)
            throw new IllegalStateException("가짜 요약기 예외");
        }
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

    /** 다음 호출이 들어오면 entered를 내리고 release까지 기다린다(요약하는 동안의 DB 상태를 보는 테스트용) */
    public void blockNext(CountDownLatch entered, CountDownLatch release) {
        blockNext.set(new CountDownLatch[] {entered, release});
    }

    /** 다음 한 번 예외를 던진다(계약 위반 흉내) */
    public void throwNext() {
        throwNext.set(true);
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
        throwNext.set(false);
        blockNext.set(null);
        calls.set(0);
        lastTurns.set(null);
    }
}
