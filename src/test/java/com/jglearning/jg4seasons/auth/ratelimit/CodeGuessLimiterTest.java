package com.jglearning.jg4seasons.auth.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jglearning.jg4seasons.MutableClock;
import com.jglearning.jg4seasons.auth.AuthErrorCode;
import com.jglearning.jg4seasons.infra.web.error.ApiException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** 가입 코드 찍기 한도: 없는 코드만 세고, 한도가 찬 IP는 조회 전에 막는다(ADR 0033 보강). */
class CodeGuessLimiterTest {

    static final String IP = "198.51.100.1";

    final MutableClock clock = new MutableClock(Instant.parse("2026-11-20T00:00:00Z"));
    final CodeGuessLimiter limiter = new CodeGuessLimiter(new FixedWindowCounter(clock, 10, Duration.ofMinutes(1)));

    private void miss(String ip) {
        assertThat(limiter.guarded(ip, Optional::empty)).isEmpty();
    }

    private static void assertRateLimited(Throwable thrown) {
        assertThat(thrown).isInstanceOf(ApiException.class);
        assertThat(((ApiException) thrown).errorCode()).isEqualTo(AuthErrorCode.RATE_LIMITED);
    }

    @Test
    void 있는_값은_세지_않는다() {
        for (int i = 0; i < 30; i++) {
            assertThat(limiter.guarded(IP, () -> Optional.of("반")))
                    .as("%d번째", i + 1)
                    .contains("반");
        }
        for (int i = 0; i < 10; i++) {
            miss(IP); // 한도 10번이 처음부터 남아 있다(예외 없음)
        }
    }

    @Test
    void 없는_값_10번_뒤에는_조회하지_않고_429() {
        for (int i = 0; i < 10; i++) {
            miss(IP);
        }
        AtomicInteger lookups = new AtomicInteger();

        assertThatThrownBy(() -> limiter.guarded(IP, () -> {
                    lookups.incrementAndGet();
                    return Optional.of("반");
                }))
                .as("있는 값이어도")
                .satisfies(CodeGuessLimiterTest::assertRateLimited);
        assertThat(lookups).as("조회하지 않는다").hasValue(0);
        assertThatThrownBy(() -> limiter.guarded(IP, Optional::empty))
                .as("없는 값")
                .satisfies(CodeGuessLimiterTest::assertRateLimited);
    }

    @Test
    void 다른_IP는_따로_센다() {
        for (int i = 0; i < 10; i++) {
            miss(IP);
        }

        assertThat(limiter.guarded("198.51.100.2", () -> Optional.of("반"))).contains("반");
    }

    @Test
    void 창이_지나면_풀린다() {
        for (int i = 0; i < 10; i++) {
            miss(IP);
        }
        clock.advance(Duration.ofMinutes(1).minusMillis(1));
        assertThatThrownBy(() -> limiter.guarded(IP, () -> Optional.of("반")))
                .as("창 끝 1ms 전")
                .satisfies(CodeGuessLimiterTest::assertRateLimited);

        clock.advance(Duration.ofMillis(1));

        assertThat(limiter.guarded(IP, () -> Optional.of("반"))).contains("반");
    }

    @Test
    void 조회하는_사이_한도가_차면_없는_값은_429() {
        for (int i = 0; i < 9; i++) {
            miss(IP);
        }

        // 조회 중에 다른 요청이 10번째를 채운다 → 이 요청은 한도를 넘겨 "없음"을 받아 가지 못한다
        assertThatThrownBy(() -> limiter.guarded(IP, () -> {
                    miss(IP);
                    return Optional.empty();
                }))
                .satisfies(CodeGuessLimiterTest::assertRateLimited);
    }
}
