package com.jglearning.jg4seasons.auth.token;

import java.time.Duration;

/** 쿠키로만 나가는 토큰 묶음. 응답 바디에 싣지 않는다(ADR-18). */
public record IssuedTokens(String access, String refresh, String csrf, Duration refreshTtl) {

    @Override
    public String toString() {
        return "IssuedTokens[refreshTtl=" + refreshTtl + "]";
    }
}
