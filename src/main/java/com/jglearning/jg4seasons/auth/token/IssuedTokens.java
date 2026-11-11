package com.jglearning.jg4seasons.auth.token;

import com.jglearning.jg4seasons.infra.audio.AudioScope;
import java.time.Duration;
import java.util.Objects;

/**
 * 쿠키로만 나가는 토큰 묶음. 응답 바디에 싣지 않는다(ADR-18). audioScope = 이 사용자의 음원 쿠키 범위(ADR 0031 결정 6) — 묶음을 만드는 곳(TokenIssuer)이 정해
 * 로그인·refresh가 같은 규칙을 거친다.
 */
public record IssuedTokens(String access, String refresh, String csrf, Duration refreshTtl, AudioScope audioScope) {

    public IssuedTokens {
        Objects.requireNonNull(audioScope, "audioScope");
    }

    @Override
    public String toString() {
        return "IssuedTokens[refreshTtl=" + refreshTtl + ", audioScope=" + audioScope + "]";
    }
}
