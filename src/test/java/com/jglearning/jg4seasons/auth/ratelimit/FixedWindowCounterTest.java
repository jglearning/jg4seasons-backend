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
    void 끝난_창_정리는_창마다_한_번만_한다() {
        // 정리 기준 2개, 상한 넉넉히. 키가 많을 때 호출마다 전체를 훑지 않게(CPU 증폭 방지)
        FixedWindowCounter small = new FixedWindowCounter(clock, 10, Duration.ofMinutes(1), 2, 1_000);
        small.tryAcquire("a");
        small.tryAcquire("b");
        small.tryAcquire("c");
        clock.advance(Duration.ofMillis(1));
        small.tryAcquire("d"); // 기준 초과 → 정리(끝난 창 없음). 다음 정리는 1분 뒤부터
        clock.advance(Duration.ofMinutes(1).minusMillis(1).plusNanos(500_000)); // a·b·c 끝남, d는 아직

        small.tryAcquire("e");
        assertThat(small.size()).as("정리한 지 1분이 안 돼 a·b·c가 남는다").isEqualTo(5);

        clock.advance(Duration.ofMillis(1));
        small.tryAcquire("f");
        assertThat(small.size()).as("1분이 지나 끝난 창(a·b·c·d)을 지운다").isEqualTo(2);
    }

    @Test
    void 키가_상한에_차면_새_키는_거부하고_있던_키는_센다() {
        FixedWindowCounter capped = new FixedWindowCounter(clock, 10, Duration.ofMinutes(1), 1, 3);
        capped.tryAcquire("a");
        capped.tryAcquire("b");
        capped.tryAcquire("c");

        assertThat(capped.tryAcquire("d")).as("상한 3개, 끝난 창 없음").isFalse();
        assertThat(capped.tryAcquire("a")).isTrue();
        assertThat(capped.size()).isEqualTo(3);

        clock.advance(Duration.ofMinutes(1));
        assertThat(capped.tryAcquire("d")).as("창이 끝나 정리되면 다시 받는다").isTrue();
    }

    @Test
    void 창이_지난_키는_count가_0이다() {
        counter.tryAcquire("ip");
        clock.advance(Duration.ofMinutes(1));
        assertThat(counter.count("ip")).isZero();
    }
}
