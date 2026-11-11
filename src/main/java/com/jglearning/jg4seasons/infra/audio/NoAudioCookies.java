package com.jglearning.jg4seasons.infra.audio;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

/** 서명이 꺼진 환경(로컬·테스트): 음원 쿠키를 주지도 지우지도 않는다 */
public enum NoAudioCookies implements AudioCookies {
    INSTANCE;

    @Override
    public Map<String, String> issue(AudioScope scope) {
        return Map.of();
    }

    @Override
    public Set<String> names() {
        return Set.of();
    }

    @Override
    public Duration ttl() {
        return Duration.ZERO;
    }
}
