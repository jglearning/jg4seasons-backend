package com.jglearning.jg4seasons.infra.audio;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

/** 서명 쿠키: 기한 = 지금(주입된 Clock) + 수명 */
final class SignedAudioCookies implements AudioCookies {

    private final CloudFrontCookieSigner signer;
    private final Clock clock;
    private final Duration ttl;

    SignedAudioCookies(CloudFrontCookieSigner signer, Clock clock, Duration ttl) {
        this.signer = signer;
        this.clock = clock;
        this.ttl = ttl;
    }

    @Override
    public Map<String, String> issue(AudioScope scope) {
        if (scope instanceof AudioScope.None) {
            return Map.of();
        }
        return signer.cookies(clock.instant().plus(ttl), scope);
    }

    @Override
    public Set<String> names() {
        return CloudFrontCookieSigner.NAMES;
    }

    @Override
    public Duration ttl() {
        return ttl;
    }
}
