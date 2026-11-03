package com.jglearning.jg4seasons.infra.time;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 시간은 이 Clock으로만 얻는다(ArchitectureTest.시간은_주입된_Clock으로만_얻는다).
 *
 * <p>시점은 UTC로 저장한다. 진도표·회차 열림 같은 날짜 판정은 학원 타임존(기본 Asia/Seoul)으로 변환해서 한다.
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
