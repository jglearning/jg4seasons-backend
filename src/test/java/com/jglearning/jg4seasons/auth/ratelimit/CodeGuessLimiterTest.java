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
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** 가입 코드 찍기 한도: 없는 코드만 세고, 한도가 찬 IP는 조회 전에 막는다(ADR 0033 보강). */
@ExtendWith(OutputCaptureExtension.class)
class CodeGuessLimiterTest {

    static final String IP = "198.51.100.1";

    static final Instant START = Instant.parse("2026-11-20T00:00:00Z");

    final MutableClock clock = new MutableClock(START);
    final CodeGuessLimiter limiter = new CodeGuessLimiter(new FixedWindowCounter(clock, 10, Duration.ofMinutes(1)));

    private void miss(String ip) {
        assertThat(limiter.guarded(ip, Optional::empty)).isEmpty();
    }

    /** 429이고, 풀리는 시각(until)은 창 끝 — 조회 전에 막혔든 세는 순간 막혔든 같다 */
    private static void assertRateLimited(Throwable thrown) {
        assertThat(thrown).isInstanceOf(ApiException.class);
        assertThat(((ApiException) thrown).errorCode()).isEqualTo(AuthErrorCode.RATE_LIMITED);
        assertThat(((ApiException) thrown).extensions()).containsEntry("until", START.plus(Duration.ofMinutes(1)));
    }

    @Test
    void 한도가_차는_순간_한_번만_WARN을_남기고_IP는_남기지_않는다(CapturedOutput output) {
        for (int i = 0; i < 9; i++) {
            miss(IP);
        }
        assertThat(output.getOut()).as("9번").doesNotContain("code guess limit reached");

        miss(IP);
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> limiter.guarded(IP, Optional::empty)).isInstanceOf(ApiException.class);
        }

        assertThat(output.getOut())
                .containsOnlyOnce("code guess limit reached until=" + START.plus(Duration.ofMinutes(1)))
                .doesNotContain(IP);
    }

    @Test
    void 있는_값만_오면_WARN이_없다(CapturedOutput output) {
        for (int i = 0; i < 30; i++) {
            limiter.guarded(IP, () -> Optional.of("반"));
        }

        assertThat(output.getOut()).doesNotContain("code guess limit reached");
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

    @Test
    void 조회하는_사이_한도가_차도_있는_값은_돌려준다() {
        for (int i = 0; i < 9; i++) {
            miss(IP);
        }

        // 받아들인 틈(ADR 0033 보강): 확인을 통과한 뒤 조회 중에 찬 한도는 있는 값을 막지 못한다. 닫으려면 있는 값도 조회 동안 칸을 차지해야
        // 해서 교실에서 한꺼번에 들어올 때 맞는 코드가 429가 된다
        assertThat(limiter.guarded(IP, () -> {
                    miss(IP);
                    return Optional.of("반");
                }))
                .contains("반");
    }

    @Test
    void 조회가_예외를_던지면_세지_않고_그대로_올린다() {
        IllegalStateException dbDown = new IllegalStateException("db down");
        for (int i = 0; i < 20; i++) {
            assertThatThrownBy(() -> limiter.guarded(IP, () -> {
                        throw dbDown;
                    }))
                    .isSameAs(dbDown);
        }

        // 장애 동안의 요청이 찍기로 세졌다면 장애가 풀린 뒤에도 이 IP는 429다
        for (int i = 0; i < 10; i++) {
            miss(IP);
        }
    }
}
