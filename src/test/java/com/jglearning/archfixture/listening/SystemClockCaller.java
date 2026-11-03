package com.jglearning.archfixture.listening;

import java.time.Clock;
import java.time.Instant;

/** 위반: infra.time 밖에서 시스템 Clock을 직접 만든다. */
public class SystemClockCaller {
    public Instant now() {
        return Instant.now(Clock.systemUTC());
    }
}
