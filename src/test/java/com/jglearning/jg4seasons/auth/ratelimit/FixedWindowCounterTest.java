package com.jglearning.jg4seasons.auth.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.MutableClock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** 키별 고정 창 카운터. 창은 그 키의 첫 기록 시각부터. */
class FixedWindowCounterTest {

    final MutableClock clock = new MutableClock(Instant.parse("2026-11-05T00:00:00Z"));
    final FixedWindowCounter counter = new FixedWindowCounter(clock, 10, Duration.ofMinutes(1));

    @Test
    void 한도까지는_허용하고_넘으면_거부한다() {
        for (int i = 0; i < 10; i++) {
            assertThat(counter.tryAcquire("ip")).as("%d번째", i + 1).isTrue();
        }
        assertThat(counter.tryAcquire("ip")).isFalse();
        assertThat(counter.count("ip")).isEqualTo(10);
    }

    @Test
    void 창이_지나면_다시_허용한다() {
        for (int i = 0; i < 10; i++) {
            counter.tryAcquire("ip");
        }
        clock.advance(Duration.ofMinutes(1).minusMillis(1));
        assertThat(counter.tryAcquire("ip")).as("창 끝 1ms 전").isFalse();

        clock.advance(Duration.ofMillis(1));
        assertThat(counter.tryAcquire("ip")).as("창이 끝난 순간").isTrue();
        assertThat(counter.count("ip")).isEqualTo(1);
    }

    @Test
    void 키가_다르면_따로_센다() {
        for (int i = 0; i < 10; i++) {
            counter.tryAcquire("a");
        }
        assertThat(counter.tryAcquire("a")).isFalse();
        assertThat(counter.tryAcquire("b")).isTrue();
    }

    @Test
    void 초기화하면_처음부터_센다() {
        for (int i = 0; i < 10; i++) {
            counter.tryAcquire("ip");
        }
        counter.reset("ip");
        assertThat(counter.count("ip")).isZero();
        assertThat(counter.tryAcquire("ip")).isTrue();
    }

    @Test
    void 창이_지난_키는_count가_0이다() {
        counter.tryAcquire("ip");
        clock.advance(Duration.ofMinutes(1));
        assertThat(counter.count("ip")).isZero();
    }
}
