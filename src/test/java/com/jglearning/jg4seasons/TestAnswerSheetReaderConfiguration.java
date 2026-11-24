package com.jglearning.jg4seasons;

import com.jglearning.jg4seasons.wordtest.reading.FakeAnswerSheetReader;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** 통합 테스트의 답안지 읽기: 가짜(외부 호출 0). 운영 설정 빈(test 프로파일은 type fake → 읽기 없음)을 @Primary로 가린다 */
@TestConfiguration(proxyBeanMethods = false)
class TestAnswerSheetReaderConfiguration {

    @Bean
    @Primary
    FakeAnswerSheetReader fakeAnswerSheetReader() {
        return new FakeAnswerSheetReader();
    }
}
