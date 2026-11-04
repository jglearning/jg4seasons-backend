package com.jglearning.jg4seasons.auth.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 키별 고정 창 카운터(인메모리, 단일 인스턴스 전제 — ADR-22). 창은 그 키의 첫 기록 시각부터 window 동안이다.
 *
 * <p>메모리: 키가 sweepThreshold를 넘으면 끝난 창을 지운다. 정리는 window마다 최대 한 번이다(키가 많을 때 호출마다 전체를 훑지 않게). 정리
 * 뒤에도 키가 hardCap에 차 있으면 새 키는 거부(false)하고, 이미 있는 키는 계속 센다 — IP를 바꿔 가며 찍는 공격 중에는 처음 보는 IP가 한도에 걸린다.
 * 서버를 2대 이상으로 늘리면 DB나 Redis로 옮긴다(ADR-22 재검토).
 */
public class FixedWindowCounter {

    static final int SWEEP_THRESHOLD = 10_000;
    static final int HARD_CAP = 100_000;

    private static final Logger log = LoggerFactory.getLogger(FixedWindowCounter.class);

    private record Window(Instant start, int count) {}

    private final Clock clock;
    private final int limit;
    private final Duration window;
    private final int sweepThreshold;
    private final int hardCap;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private volatile Instant lastSweep;

    public FixedWindowCounter(Clock clock, int limit, Duration window) {
        this(clock, limit, window, SWEEP_THRESHOLD, HARD_CAP);
    }

    FixedWindowCounter(Clock clock, int limit, Duration window, int sweepThreshold, int hardCap) {
        this.clock = clock;
        this.limit = limit;
        this.window = window;
        this.sweepThreshold = sweepThreshold;
        this.hardCap = hardCap;
    }

    /** 한 번 센다. 창 안에서 limit번째까지 true, 넘으면 false(세지 않음). 키 상한에 차 있으면 새 키도 false */
    public boolean tryAcquire(String key) {
        Instant now = clock.instant();
        if (windows.size() > sweepThreshold) {
            sweepIfDue(now);
        }
        if (windows.size() >= hardCap && !windows.containsKey(key)) {
            return false;
        }
        boolean[] acquired = {false};
        windows.compute(key, (k, current) -> {
            if (current == null || expired(current, now)) {
                acquired[0] = true;
                return new Window(now, 1);
            }
            if (current.count() < limit) {
                acquired[0] = true;
                return new Window(current.start(), current.count() + 1);
            }
            return current;
        });
        return acquired[0];
    }

    /** 지금 창의 횟수. 창이 끝났으면 0 */
    public int count(String key) {
        Window current = windows.get(key);
        return current == null || expired(current, clock.instant()) ? 0 : current.count();
    }

    public void reset(String key) {
        windows.remove(key);
    }

    /** 들고 있는 키 수(끝난 창 포함). 테스트용 */
    int size() {
        return windows.size();
    }

    private synchronized void sweepIfDue(Instant now) {
        if (lastSweep != null && now.isBefore(lastSweep.plus(window))) {
            return;
        }
        lastSweep = now;
        windows.values().removeIf(w -> expired(w, now));
        if (windows.size() >= hardCap) {
            log.warn(
                    "rate limit keys at hard cap size={} — new keys are rejected until the window ends",
                    windows.size());
        }
    }

    private boolean expired(Window w, Instant now) {
        return !now.isBefore(w.start().plus(window));
    }
}
