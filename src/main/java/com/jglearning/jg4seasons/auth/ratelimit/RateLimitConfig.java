package com.jglearning.jg4seasons.auth.ratelimit;

import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 인증 요청 한도(API 명세 §0.5). */
@Configuration(proxyBeanMethods = false)
public class RateLimitConfig {

    /** 가입 코드 찍기: IP당 1분 10회. verify 호출과, 없는 가입 코드로 한 로그인 시도가 함께 센다 */
    public static final int CODE_GUESS_LIMIT = 10;

    public static final Duration CODE_GUESS_WINDOW = Duration.ofMinutes(1);

    /** 없는 교사 로그인 아이디: IP당 1분 10회(학생 가입 코드와 따로) */
    @Bean
    public StaffLoginGuessLimiter staffLoginGuessLimiter(Clock clock) {
        return new StaffLoginGuessLimiter(new FixedWindowCounter(clock, CODE_GUESS_LIMIT, CODE_GUESS_WINDOW));
    }

    @Bean
    public CodeGuessLimiter codeGuessLimiter(Clock clock) {
        return new CodeGuessLimiter(new FixedWindowCounter(clock, CODE_GUESS_LIMIT, CODE_GUESS_WINDOW));
    }
}
