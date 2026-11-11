package com.jglearning.jg4seasons.infra.audio;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

/**
 * 음원(/audio/*)용 쿠키(ADR 0028). auth가 로그인·refresh 때 {@link #issue(AudioScope)}를, 로그아웃 때 {@link #names()}를 받아 쿠키를 만든다. 서명이 꺼져
 * 있으면 비어 있다({@link NoAudioCookies}).
 */
public interface AudioCookies {

    /** 쿠키 이름 → 값. 부를 때마다 새 기한으로 그 범위를 서명한다. 범위가 none이거나 서명이 꺼져 있으면 빈 맵 */
    Map<String, String> issue(AudioScope scope);

    /** 지울 때 쓸 쿠키 이름 */
    Set<String> names();

    /** 쿠키 수명(Max-Age) — 정책 기한과 같다 */
    Duration ttl();
}
