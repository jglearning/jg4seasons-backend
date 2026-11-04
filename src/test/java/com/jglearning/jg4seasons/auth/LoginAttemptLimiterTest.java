package com.jglearning.jg4seasons.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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

    /** 로그인 서비스와 같은 순서: 시도 시작(먼저 셈) → 틀림 */
    private void fail(int times, long classId, String ip) {
        for (int i = 0; i < times; i++) {
            assertThat(limiter.beginAttempt(classId, ip)).as("시도 %d 시작", i + 1).isEmpty();
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

        assertThat(limiter.beginAttempt(1, "ip")).as("5번째 시도는 확인까지 간다").isEmpty();
        Optional<Instant> until = limiter.recordFailure(1, "ip");

        assertThat(until).contains(START.plus(Duration.ofMinutes(10)));
        assertThat(limiter.lockedUntil(1, "ip")).contains(START.plus(Duration.ofMinutes(10)));
    }

    @Test
    void 잠금이_걸리면_반_id와_해제_시각을_INFO로_남기고_IP는_남기지_않는다(CapturedOutput output) {
        fail(5, 7, "203.0.113.9");

        assertThat(output.getOut())
                .containsOnlyOnce("login locked classId=7 until=" + START.plus(Duration.ofMinutes(10)))
                .doesNotContain("203.0.113.9");
    }

    @Test
    void 다섯_번째_시도가_맞으면_잠금_로그를_남기지_않는다(CapturedOutput output) {
        fail(4, 8, "ip");
        limiter.beginAttempt(8, "ip");
        limiter.recordSuccess(8, "ip");

        assertThat(output.getOut()).doesNotContain("login locked classId=8");
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
    void 잠긴_뒤의_시도는_확인_전에_거부한다() {
        fail(5, 1, "ip");

        assertThat(limiter.beginAttempt(1, "ip")).contains(START.plus(Duration.ofMinutes(10)));
    }

    @Test
    void 다섯_번째_시도가_맞으면_잠그지_않는다() {
        fail(4, 1, "ip");

        assertThat(limiter.beginAttempt(1, "ip")).isEmpty();
        limiter.recordSuccess(1, "ip");

        assertThat(limiter.lockedUntil(1, "ip")).isEmpty();
    }

    @Test
    void 동시에_와도_확인까지_가는_시도는_5번뿐이다() throws Exception {
        // 확인(잠금 검사) 뒤 실패 기록 사이에 다른 요청이 끼면 5번보다 많이 찍을 수 있다 → 시작할 때 먼저 센다
        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger allowed = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                if (limiter.beginAttempt(1, "ip").isEmpty()) {
                    allowed.incrementAndGet();
                }
                return null;
            }));
        }
        ready.await();
        go.countDown();
        for (Future<?> future : futures) {
            future.get(10, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(allowed.get()).isEqualTo(5);
    }

    @Test
    void 성공해도_그동안의_실패는_지우지_않는다() {
        // 같은 반 학생이 남의 코드를 4번 찍고 자기 코드로 성공해 횟수를 지우는 반복을 막는다. 성공한 시도 1회만 되돌린다
        fail(4, 1, "ip");
        assertThat(limiter.beginAttempt(1, "ip")).isEmpty();
        limiter.recordSuccess(1, "ip");

        fail(1, 1, "ip");

        assertThat(limiter.lockedUntil(1, "ip")).as("실패 4 + 1 = 5").isPresent();
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
            fail(1, 1, "ip-" + i);
        }
        assertThat(limiter.isAlerting(1)).as("19회").isFalse();

        fail(1, 1, "ip-19");

        assertThat(limiter.isAlerting(1)).as("20회").isTrue();
        assertThat(limiter.isAlerting(2)).as("다른 반").isFalse();
        assertThat(output.getOut()).containsOnlyOnce("login failures over threshold classId=1");

        fail(1, 1, "ip-20");
        fail(1, 1, "ip-21");
        assertThat(output.getOut())
                .as("경고 중 실패가 이어져도 한 번만")
                .containsOnlyOnce("login failures over threshold classId=1");
    }

    @Test
    void 잠긴_동안_시도해도_해제_시각은_늘지_않는다() {
        fail(5, 1, "ip");

        clock.advance(Duration.ofMinutes(5));
        assertThat(limiter.beginAttempt(1, "ip")).contains(START.plus(Duration.ofMinutes(10)));

        clock.advance(Duration.ofMinutes(5));
        assertThat(limiter.beginAttempt(1, "ip")).as("처음 잠근 지 10분").isEmpty();
    }

    @Test
    void 십분_안의_실패는_이어서_센다() {
        fail(4, 1, "ip");
        clock.advance(Duration.ofMinutes(10).minusMillis(1));

        fail(1, 1, "ip");

        assertThat(limiter.lockedUntil(1, "ip")).isPresent();
    }

    @Test
    void 정리해도_잠긴_항목은_남는다() {
        // 정리 기준 1개: 처음 키 3개를 넣을 때 정리가 한 번 돌고, 다음 정리는 10분 뒤
        LoginAttemptLimiter small = new LoginAttemptLimiter(clock, 1, 1_000);
        failOn(small, 1, "b");
        failOn(small, 1, "c");
        failOn(small, 1, "x");
        clock.advance(Duration.ofMinutes(5));
        for (int i = 0; i < 5; i++) {
            failOn(small, 1, "a"); // 5분에 잠김 → 15분에 풀림
        }

        clock.advance(Duration.ofMinutes(5));
        small.beginAttempt(1, "d"); // 10분: 정리 — b·c·x는 창이 끝났고 a는 잠겨 있다

        assertThat(small.lockedUntil(1, "a")).contains(START.plus(Duration.ofMinutes(15)));
    }

    @Test
    void 상한에_차면_새_IP는_반별_공용_칸에서_함께_센다() {
        LoginAttemptLimiter capped = new LoginAttemptLimiter(clock, 1_000, 3);
        failOn(capped, 1, "k1");
        failOn(capped, 2, "k2");
        failOn(capped, 3, "k3");

        for (int i = 1; i <= 5; i++) {
            failOn(capped, 1, "n" + i);
        }

        assertThat(capped.lockedUntil(1, "n6")).as("반 1의 새 IP는 공용 칸이 잠김").isPresent();
        assertThat(capped.lockedUntil(2, "n6")).as("다른 반의 공용 칸은 따로").isEmpty();
        assertThat(capped.lockedUntil(1, "k1")).as("있던 키는 영향 없음").isEmpty();
    }

    private static void failOn(LoginAttemptLimiter target, long classId, String ip) {
        target.beginAttempt(classId, ip);
        target.recordFailure(classId, ip);
    }

    @Test
    void 한_시간이_지난_실패는_경고에서_빠진다() {
        for (int i = 0; i < 20; i++) {
            fail(1, 1, "ip-" + i);
        }
        clock.advance(Duration.ofHours(1));

        assertThat(limiter.isAlerting(1)).isFalse();
    }

    @Test
    void 경고는_1시간_안의_실패로만_센다() {
        for (int i = 0; i < 10; i++) {
            fail(1, 1, "ip-" + i);
        }
        clock.advance(Duration.ofMinutes(59));
        for (int i = 10; i < 20; i++) {
            fail(1, 1, "ip-" + i);
        }
        assertThat(limiter.isAlerting(1)).as("59분 안에 20회").isTrue();

        clock.advance(Duration.ofMinutes(1));
        assertThat(limiter.isAlerting(1)).as("처음 10회가 1시간 밖으로").isFalse();
    }
}
