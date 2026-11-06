package com.jglearning.jg4seasons.infra.audio;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 음원 주소 = urlPrefix + track.file_key(예: "/" + "audio/L2-B1-05/L2-B1-05_p1_1.0.mp3"). 기본 "/"는 같은 도메인 상대 경로 — 운영은
 * CloudFront가 /audio/*를 음원 버킷으로 보낸다.
 */
@ConfigurationProperties("jg4seasons.audio")
public record AudioProperties(@DefaultValue("/") String urlPrefix) {

    public String urlOf(String fileKey) {
        return urlPrefix + fileKey;
    }
}
