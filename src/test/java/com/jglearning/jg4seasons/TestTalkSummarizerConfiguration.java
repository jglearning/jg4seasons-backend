package com.jglearning.jg4seasons;

import com.jglearning.jg4seasons.talk.summary.FakeTalkSummarizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 통합 테스트의 프리토킹 요약: 가짜(외부 호출 0). 운영 설정 빈(test 프로파일은 type fake)을 @Primary로 가리고 테스트가 이 빈을 주입받아 실패를 넣는다. 컨텍스트 하나에
 * 공유되므로 쓰는 테스트는 @BeforeEach에서 reset()을 부른다
 */
@TestConfiguration(proxyBeanMethods = false)
class TestTalkSummarizerConfiguration {

    @Bean
    @Primary
    FakeTalkSummarizer fakeTalkSummarizer() {
        return new FakeTalkSummarizer();
    }
}
