package com.jglearning.jg4seasons.talk.realtime;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 외부 호출 없는 발급기(로컬 type fake·통합 테스트). 부를 때마다 다른 고정 형식 키(ek_fake_1, ek_fake_2…)와 시계 + 수명을 돌려준다. 테스트는 failNext로 실패를, lastRequest로
 * 받은 설정(지시문·음성)을 본다
 */
public class FakeRealtimeSessionIssuer implements RealtimeSessionIssuer {

    private final Clock clock;
    private final AtomicInteger counter = new AtomicInteger();
    private final AtomicBoolean failNext = new AtomicBoolean();
    private final AtomicReference<RealtimeSessionRequest> lastRequest = new AtomicReference<>();
    private final AtomicReference<Runnable> beforeNextIssue = new AtomicReference<>();

    public FakeRealtimeSessionIssuer(Clock clock) {
        this.clock = clock;
    }

    @Override
    public IssuedSecret issue(RealtimeSessionRequest request) {
        Runnable action = beforeNextIssue.getAndSet(null);
        if (action != null) {
            action.run();
        }
        lastRequest.set(request);
        if (failNext.getAndSet(false)) {
            throw new RealtimeUnavailableException("가짜 발급기 실패");
        }
        Instant expiresAt = clock.instant().truncatedTo(ChronoUnit.SECONDS).plusSeconds(request.keyTtlSeconds());
        return new IssuedSecret("ek_fake_" + counter.incrementAndGet(), expiresAt, request.model());
    }

    /** 다음 발급 직전에 한 번 실행(같은 clientSessionId 경합을 순서대로 재현할 때) */
    public void beforeNextIssue(Runnable action) {
        beforeNextIssue.set(action);
    }

    /** 다음 한 번만 실패 */
    public void failNext() {
        failNext.set(true);
    }

    public RealtimeSessionRequest lastRequest() {
        return lastRequest.get();
    }

    /** 테스트 사이에 실패 예약·마지막 요청을 지운다 */
    public void reset() {
        failNext.set(false);
        lastRequest.set(null);
        beforeNextIssue.set(null);
    }
}
