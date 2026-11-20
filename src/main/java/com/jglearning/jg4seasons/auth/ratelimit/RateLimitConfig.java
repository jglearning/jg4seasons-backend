package com.jglearning.jg4seasons.auth.ratelimit;

import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 인증 요청 한도(API 명세 §0.5). */
@Configuration(proxyBeanMethods = false)
public class RateLimitConfig {

    /** 가입 코드 찍기: 없는 가입 코드만 IP당 1분 10회. 확인(1.1)과 학생 로그인(1.2)이 함께 센다. 교사·관리자 로그인도 같은 값을 쓴다 */
    public static final int CODE_GUESS_LIMIT = 10;

    public static final Duration CODE_GUESS_WINDOW = Duration.ofMinutes(1);

    /** 교사·관리자 로그인 시도 전부: IP당 1분 10회(학생 가입 코드와 따로) */
    @Bean
    public StaffLoginGuessLimiter staffLoginGuessLimiter(Clock clock) {
        return new StaffLoginGuessLimiter(new FixedWindowCounter(clock, CODE_GUESS_LIMIT, CODE_GUESS_WINDOW));
    }

    @Bean
    public CodeGuessLimiter codeGuessLimiter(Clock clock) {
        return new CodeGuessLimiter(new FixedWindowCounter(clock, CODE_GUESS_LIMIT, CODE_GUESS_WINDOW));
    }
}
