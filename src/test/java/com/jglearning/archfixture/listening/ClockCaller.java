package com.jglearning.archfixture.listening;

import java.time.Clock;
import java.time.Instant;

/** 정상: 주입받은 Clock으로 now(clock)을 부른다. */
public class ClockCaller {
    private final Clock clock;

    public ClockCaller(Clock clock) {
        this.clock = clock;
    }

    public Instant now() {
        return Instant.now(clock);
    }
}
