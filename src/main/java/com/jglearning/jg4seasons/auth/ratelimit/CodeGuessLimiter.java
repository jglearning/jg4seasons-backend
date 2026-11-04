package com.jglearning.jg4seasons.auth.ratelimit;

import com.jglearning.jg4seasons.auth.AuthErrorCode;
import com.jglearning.jg4seasons.infra.web.error.ApiException;

/** 가입 코드 찍기 한도(IP당 1분 10회). 이름 있는 빈이라 다른 카운터와 섞여 주입되지 않는다. */
public class CodeGuessLimiter {

    private final FixedWindowCounter counter;

    CodeGuessLimiter(FixedWindowCounter counter) {
        this.counter = counter;
    }

    /** 한 번 센다. 넘으면 429 RATE_LIMITED */
    public void acquire(String clientIp) {
        if (!counter.tryAcquire(clientIp)) {
            throw new ApiException(AuthErrorCode.RATE_LIMITED, "잠시 후 다시 시도해 주세요");
        }
    }
}
