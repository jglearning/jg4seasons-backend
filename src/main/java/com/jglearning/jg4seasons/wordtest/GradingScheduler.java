package com.jglearning.jg4seasons.wordtest;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 1초마다 채점 워커를 돌린다(작업이 있으면 그 틱에서 최대 10건 이어서). Spring 기본 스케줄러는 스레드 하나라 한 번에 1건씩. 테스트는
 * {@code jg4seasons.word-test.worker.scheduled=false}로 끄고 runOnce()를 직접 부른다
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "jg4seasons.word-test.worker.scheduled", havingValue = "true", matchIfMissing = true)
class GradingScheduler {

    static final int MAX_PER_TICK = 10;

    private final GradingWorker worker;

    GradingScheduler(GradingWorker worker) {
        this.worker = worker;
    }

    @Scheduled(fixedDelay = 1000)
    void tick() {
        for (int i = 0; i < MAX_PER_TICK && worker.runOnce() == 1; i++) {
            // 대기 작업이 있는 동안 이어서
        }
    }
}
