package com.jglearning.jg4seasons;

import com.jglearning.jg4seasons.talk.realtime.FakeRealtimeSessionIssuer;
import java.time.Clock;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** 통합 테스트의 임시 키 발급: 가짜(외부 호출 0). 운영 설정 빈(test 프로파일은 type fake)을 @Primary로 가리고 테스트가 이 빈을 주입받아 실패를 넣는다 */
@TestConfiguration(proxyBeanMethods = false)
class TestRealtimeIssuerConfiguration {

    @Bean
    @Primary
    FakeRealtimeSessionIssuer fakeRealtimeSessionIssuer(Clock clock) {
        return new FakeRealtimeSessionIssuer(clock);
    }
}
