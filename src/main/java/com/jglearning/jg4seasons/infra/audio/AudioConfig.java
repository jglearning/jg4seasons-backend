package com.jglearning.jg4seasons.infra.audio;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ResponseCookie;

@Configuration
@EnableConfigurationProperties({AudioProperties.class, AudioSigningProperties.class})
class AudioConfig {

    private static final Logger log = LoggerFactory.getLogger(AudioConfig.class);

    /**
     * 서명을 켰으면 개인 키를 여기서 읽고 쿠키를 한 번 만들어 본다 — 키·서명·쿠키 값 중 하나라도 안 되면 서버가 뜨지 않는다(배포가 이전 슬롯에 머문다,
     * ADR 0028). 로그인 요청 때 처음 실패하지 않게. 켜짐·꺼짐은 로그로 남긴다(개인 키 없이)
     */
    @Bean
    AudioCookies audioCookies(AudioSigningProperties signing, Clock clock) {
        if (!signing.enabled()) {
            log.info("음원 서명 쿠키 꺼짐 — /audio/*는 서명 없이 열리거나(로컬) CloudFront가 403으로 막는다");
            return NoAudioCookies.INSTANCE;
        }
        CloudFrontCookieSigner signer = new CloudFrontCookieSigner(
                signing.keyPairId(), CloudFrontCookieSigner.parsePrivateKey(signing.privateKey()), signing.resource());
        SignedAudioCookies cookies = new SignedAudioCookies(signer, clock, signing.ttl());
        try {
            cookies.issue(AudioScope.all())
                    .forEach((name, value) -> ResponseCookie.from(name, value).build());
        } catch (RuntimeException e) {
            // 메시지에 쿠키 값·키 조각을 넣지 않는다
            throw new IllegalStateException("음원 서명 쿠키를 만들 수 없다(jg4seasons.audio.signing.* 확인)");
        }
        log.info("음원 서명 쿠키 켜짐: {}", signing);
        return cookies;
    }
}
