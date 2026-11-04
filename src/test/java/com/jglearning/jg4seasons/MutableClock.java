package com.jglearning.jg4seasons;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** 테스트에서 시간을 앞으로 보내는 시계(UTC). 스레드 사이에서 같은 시각을 보도록 volatile. */
public final class MutableClock extends Clock {

    private volatile Instant now;

    public MutableClock(Instant start) {
        this.now = start;
    }

    public void advance(Duration duration) {
        now = now.plus(duration);
    }

    /** 현재 시스템 시각으로 되돌린다. 공유 컨텍스트의 시계를 움직인 테스트는 끝에 부른다 */
    public void reset() {
        now = Clock.systemUTC().instant();
    }

    public void set(Instant instant) {
        now = instant;
    }

    @Override
    public Instant instant() {
        return now;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return Clock.fixed(now, zone);
    }
}
