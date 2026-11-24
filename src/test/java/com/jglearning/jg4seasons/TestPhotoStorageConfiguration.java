package com.jglearning.jg4seasons;

import com.jglearning.jg4seasons.infra.storage.MemoryPhotoStorage;
import java.time.Clock;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** 통합 테스트의 사진 저장소: 메모리(외부 호출 0). 운영 설정 빈(test 프로파일은 type memory → 꺼진 저장소)을 @Primary로 가린다 */
@TestConfiguration(proxyBeanMethods = false)
class TestPhotoStorageConfiguration {

    @Bean
    @Primary
    MemoryPhotoStorage memoryPhotoStorage(Clock clock) {
        return new MemoryPhotoStorage(clock);
    }
}
