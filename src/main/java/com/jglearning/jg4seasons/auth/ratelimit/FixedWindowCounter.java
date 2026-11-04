package com.jglearning.jg4seasons.auth.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 키별 고정 창 카운터(인메모리, 단일 인스턴스 전제 — ADR-22). 창은 그 키의 첫 기록 시각부터 window 동안이다.
 *
 * <p>키가 MAX_KEYS를 넘으면 창이 끝난 키를 지워 메모리가 계속 늘지 않게 한다. 서버를 2대 이상으로 늘리면 DB나 Redis로 옮긴다(ADR-22 재검토).
 */
public class FixedWindowCounter {

    static final int MAX_KEYS = 10_000;

    private record Window(Instant start, int count) {}

    private final Clock clock;
    private final int limit;
    private final Duration window;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public FixedWindowCounter(Clock clock, int limit, Duration window) {
        this.clock = clock;
        this.limit = limit;
        this.window = window;
    }

    /** 한 번 센다. 창 안에서 limit번째까지 true, 넘으면 false(세지 않음) */
    public boolean tryAcquire(String key) {
        Instant now = clock.instant();
        if (windows.size() > MAX_KEYS) {
            windows.values().removeIf(w -> expired(w, now));
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

    private boolean expired(Window w, Instant now) {
        return !now.isBefore(w.start().plus(window));
    }
}
