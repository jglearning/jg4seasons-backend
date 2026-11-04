package com.jglearning.jg4seasons.auth.ratelimit;

import com.jglearning.jg4seasons.auth.AuthErrorCode;
import com.jglearning.jg4seasons.infra.web.error.ApiException;

/**
 * 교사·관리자 로그인 시도 한도(IP당 1분 10회, 성공·실패·없는 아이디 모두). 있는 계정들에 나눠 찍는 대입을 막고 429가 계정 존재와 무관하게 나오게.
 * 학생 가입 코드 한도와 따로 센다 — 교무실 IP 하나에서 섞이지 않게.
 */
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
