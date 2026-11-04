package com.jglearning.jg4seasons;

import java.time.Clock;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 통합 테스트의 시계. 운영 ClockConfig(시스템 UTC) 대신 앞으로 보낼 수 있는 시계를 주입한다(컨텍스트 1개 유지 — @MockitoBean 쓰지 않음).
 *
 * <p>시간을 움직인 테스트는 끝에 {@code clock.reset()}으로 현재 시각에 되돌린다.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestClockConfiguration {

    @Bean
    @Primary
    MutableClock testClock() {
        return new MutableClock(Clock.systemUTC().instant());
    }
}
