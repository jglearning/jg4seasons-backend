package com.jglearning.jg4seasons.infra.audio;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({AudioProperties.class, AudioSigningProperties.class})
class AudioConfig {

    /** 서명을 켰으면 개인 키를 여기서 읽는다 — 못 읽으면 서버가 뜨지 않는다(ADR 0028) */
    @Bean
    AudioCookies audioCookies(AudioSigningProperties signing, Clock clock) {
        if (!signing.enabled()) {
            return NoAudioCookies.INSTANCE;
        }
        CloudFrontCookieSigner signer = new CloudFrontCookieSigner(
                signing.keyPairId(), CloudFrontCookieSigner.parsePrivateKey(signing.privateKey()), signing.resource());
        return new SignedAudioCookies(signer, clock, signing.ttl());
    }
}
