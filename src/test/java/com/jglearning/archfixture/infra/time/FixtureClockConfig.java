package com.jglearning.archfixture.infra.time;

import java.time.Clock;

/** 정상: 시스템 Clock은 infra.time에서만 만든다. */
public class FixtureClockConfig {
    public Clock clock() {
        return Clock.systemUTC();
    }
}
