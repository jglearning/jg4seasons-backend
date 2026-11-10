package com.jglearning.jg4seasons.infra.audio;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

/** 서명 쿠키: 기한 = 지금(주입된 Clock) + 수명 */
final class SignedAudioCookies implements AudioCookies {

    private static final Set<String> NAMES =
            Set.of(CloudFrontCookieSigner.POLICY, CloudFrontCookieSigner.SIGNATURE, CloudFrontCookieSigner.KEY_PAIR_ID);

    private final CloudFrontCookieSigner signer;
    private final Clock clock;
    private final Duration ttl;

    SignedAudioCookies(CloudFrontCookieSigner signer, Clock clock, Duration ttl) {
        this.signer = signer;
        this.clock = clock;
        this.ttl = ttl;
    }

    @Override
    public Map<String, String> issue() {
        return signer.cookies(clock.instant().plus(ttl));
    }

    @Override
    public Set<String> names() {
        return NAMES;
    }

    @Override
    public Duration ttl() {
        return ttl;
    }
}
