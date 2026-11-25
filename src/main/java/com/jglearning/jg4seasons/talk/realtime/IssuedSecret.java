package com.jglearning.jg4seasons.talk.realtime;

import java.time.Instant;

/** 발급받은 임시 키. clientSecret은 브라우저에만 넘기고 로그·toString에 넣지 않는다 */
public record IssuedSecret(String clientSecret, Instant expiresAt, String model) {

    @Override
    public String toString() {
        return "IssuedSecret[expiresAt=" + expiresAt + ", model=" + model + "]";
    }
}
