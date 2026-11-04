package com.jglearning.jg4seasons.auth.ratelimit;

import com.jglearning.jg4seasons.auth.AuthErrorCode;
import com.jglearning.jg4seasons.infra.web.error.ApiException;

/** 없는 로그인 아이디 찍기 한도(IP당 1분 10회). 학생 가입 코드 한도와 따로 센다 — 교무실 IP 하나에서 섞이지 않게. */
public class StaffLoginGuessLimiter {

    private final FixedWindowCounter counter;

    StaffLoginGuessLimiter(FixedWindowCounter counter) {
        this.counter = counter;
    }

    /** 한 번 센다. 넘으면 429 RATE_LIMITED */
    public void acquire(String clientIp) {
        if (!counter.tryAcquire(clientIp)) {
            throw new ApiException(AuthErrorCode.RATE_LIMITED, "잠시 후 다시 시도해 주세요");
        }
    }
}
