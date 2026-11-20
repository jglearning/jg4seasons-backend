package com.jglearning.jg4seasons.auth.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
    void 키가_상한에_차면_새_키는_공용_칸에서_함께_센다() {
        // 상한을 넘긴 키를 모두 한 칸에서 센다 → 공격 중에도 찍기 횟수에 상한이 남고, 이미 세던 키는 영향 없음
        FixedWindowCounter capped = new FixedWindowCounter(clock, 2, Duration.ofMinutes(1), 1, 3);
        capped.tryAcquire("a");
        capped.tryAcquire("b");
        capped.tryAcquire("c");

        assertThat(capped.tryAcquire("d")).as("공용 칸 1번째").isTrue();
        assertThat(capped.tryAcquire("e")).as("공용 칸 2번째(다른 새 키도 같은 칸)").isTrue();
        assertThat(capped.tryAcquire("f")).as("공용 칸 한도 2 초과").isFalse();
        assertThat(capped.tryAcquire("a")).as("있던 키는 따로 센다").isTrue();
        assertThat(capped.count("d")).as("새 키는 자기 칸이 없다").isZero();

        clock.advance(Duration.ofMinutes(1));
        assertThat(capped.tryAcquire("d")).as("정리되면 다시 자기 칸").isTrue();
        assertThat(capped.count("d")).isEqualTo(1);
    }

    @Test
    void 한도에_차면_isExhausted이고_물어도_세지_않는다() {
        for (int i = 0; i < 9; i++) {
            counter.tryAcquire("a");
        }
        assertThat(counter.isExhausted("a")).as("9번").isFalse();

        counter.tryAcquire("a");
        assertThat(counter.isExhausted("a")).as("10번").isTrue();
        assertThat(counter.isExhausted("a")).as("다시 물어도").isTrue();
        assertThat(counter.count("a")).as("묻는 것은 세지 않는다").isEqualTo(10);
        assertThat(counter.isExhausted("b")).as("다른 키").isFalse();
        assertThat(counter.size()).as("물어본 키를 만들지 않는다").isEqualTo(1);

        clock.advance(Duration.ofMinutes(1));
        assertThat(counter.isExhausted("a")).as("창이 끝난 뒤").isFalse();
    }

    @Test
    void 키_상한_중_새_키는_공용_칸으로_판정한다() {
        // tryAcquire와 같은 칸을 봐야 한다 — 새 키를 0으로 읽으면 상한 공격 중 "찼는지"가 늘 거짓
        FixedWindowCounter capped = new FixedWindowCounter(clock, 2, Duration.ofMinutes(1), 1, 3);
        capped.tryAcquire("a");
        capped.tryAcquire("b");
        capped.tryAcquire("c");
        assertThat(capped.isExhausted("d")).as("공용 칸이 비었다").isFalse();

        capped.tryAcquire("d");
        capped.tryAcquire("e");

        assertThat(capped.isExhausted("f")).as("처음 보는 키도 공용 칸이 찼으면 찼다").isTrue();
        assertThat(capped.isExhausted("a")).as("있던 키는 자기 칸(1번)").isFalse();
    }

    @Test
    void 동시에_세도_한도만큼만_허용한다() throws Exception {
        int threads = 32;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger allowed = new AtomicInteger();
        try {
            List<Future<?>> jobs = new ArrayList<>();
            for (int i = 0; i < 200; i++) {
                jobs.add(pool.submit(() -> {
                    start.await();
                    if (counter.tryAcquire("ip")) {
                        allowed.incrementAndGet();
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> job : jobs) {
                job.get(10, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(allowed).hasValue(10);
        assertThat(counter.count("ip")).isEqualTo(10);
    }

    @Test
    void 창이_지난_키는_count가_0이다() {
        counter.tryAcquire("ip");
        clock.advance(Duration.ofMinutes(1));
        assertThat(counter.count("ip")).isZero();
    }
}
