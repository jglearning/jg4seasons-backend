package com.jglearning.jg4seasons.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** 찍기 방지(ADR-22, AUTH-07): (반, IP) 5회 실패 → 10분 잠금, 반 전체 1시간 20회 실패 → 교사 경고. */
@ExtendWith(OutputCaptureExtension.class)
class LoginAttemptLimiterTest {

    static final Instant START = Instant.parse("2026-11-05T00:00:00Z");

    final MutableClock clock = new MutableClock(START);
    final LoginAttemptLimiter limiter = new LoginAttemptLimiter(clock);

    private void fail(int times, long classId, String ip) {
        for (int i = 0; i < times; i++) {
            limiter.recordFailure(classId, ip);
        }
    }

    @Test
    void 네_번_실패는_잠그지_않는다() {
        fail(4, 1, "ip");

        assertThat(limiter.lockedUntil(1, "ip")).isEmpty();
    }

    @Test
    void 다섯_번째_실패에_10분_잠근다() {
        fail(4, 1, "ip");

        Optional<Instant> until = limiter.recordFailure(1, "ip");

        assertThat(until).contains(START.plus(Duration.ofMinutes(10)));
        assertThat(limiter.lockedUntil(1, "ip")).contains(START.plus(Duration.ofMinutes(10)));
    }

    @Test
    void 십분이_지나면_풀린다() {
        fail(5, 1, "ip");

        clock.advance(Duration.ofMinutes(10).minusMillis(1));
        assertThat(limiter.lockedUntil(1, "ip")).as("10분 1ms 전").isPresent();

        clock.advance(Duration.ofMillis(1));
        assertThat(limiter.lockedUntil(1, "ip")).as("10분 정각").isEmpty();
    }

    @Test
    void 잠금이_풀리면_실패를_처음부터_센다() {
        fail(5, 1, "ip");
        clock.advance(Duration.ofMinutes(10));

        fail(4, 1, "ip");

        assertThat(limiter.lockedUntil(1, "ip")).isEmpty();
    }

    @Test
    void 성공하면_실패_횟수가_0이다() {
        fail(4, 1, "ip");
        limiter.recordSuccess(1, "ip");

        fail(4, 1, "ip");

        assertThat(limiter.lockedUntil(1, "ip")).isEmpty();
    }

    @Test
    void 십분이_지난_실패는_세지_않는다() {
        fail(4, 1, "ip");
        clock.advance(Duration.ofMinutes(10));

        fail(1, 1, "ip");

        assertThat(limiter.lockedUntil(1, "ip")).as("이전 4회는 창 밖").isEmpty();
    }

    @Test
    void IP가_다르면_따로_반이_다르면_따로() {
        fail(5, 1, "ip-a");

        assertThat(limiter.lockedUntil(1, "ip-b")).isEmpty();
        assertThat(limiter.lockedUntil(2, "ip-a")).isEmpty();
    }

    @Test
    void 반_전체_1시간_20회면_경고(CapturedOutput output) {
        for (int i = 0; i < 19; i++) {
            limiter.recordFailure(1, "ip-" + i);
        }
        assertThat(limiter.isAlerting(1)).as("19회").isFalse();

        limiter.recordFailure(1, "ip-19");

        assertThat(limiter.isAlerting(1)).as("20회").isTrue();
        assertThat(limiter.isAlerting(2)).as("다른 반").isFalse();
        assertThat(output.getOut()).containsOnlyOnce("login failures over threshold classId=1");
    }

    @Test
    void 한_시간이_지난_실패는_경고에서_빠진다() {
        for (int i = 0; i < 20; i++) {
            limiter.recordFailure(1, "ip-" + i);
        }
        clock.advance(Duration.ofHours(1));

        assertThat(limiter.isAlerting(1)).isFalse();
    }

    @Test
    void 경고는_1시간_안의_실패로만_센다() {
        for (int i = 0; i < 10; i++) {
            limiter.recordFailure(1, "ip-" + i);
        }
        clock.advance(Duration.ofMinutes(59));
        for (int i = 10; i < 20; i++) {
            limiter.recordFailure(1, "ip-" + i);
        }
        assertThat(limiter.isAlerting(1)).as("59분 안에 20회").isTrue();

        clock.advance(Duration.ofMinutes(1));
        assertThat(limiter.isAlerting(1)).as("처음 10회가 1시간 밖으로").isFalse();
    }
}
