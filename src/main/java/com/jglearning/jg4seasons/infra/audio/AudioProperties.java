package com.jglearning.jg4seasons.infra.audio;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 음원 주소 = urlPrefix + track.file_key(예: "/" + "audio/L2-B1-05/L2-B1-05_p1_1.0.mp3"). 기본 "/"는 같은 도메인 상대 경로 — 운영은
 * CloudFront가 /audio/*를 음원 버킷으로 보낸다.
 */
@ConfigurationProperties("jg4seasons.audio")
public record AudioProperties(@DefaultValue("/") String urlPrefix) {

    /** 접두어는 "/"로 끝나야 한다 — 아니면 서버가 뜨지 않는다(끝 "/"가 빠지면 …exampleaudio/… 같은 깨진 주소가 조용히 나간다) */
    public AudioProperties {
        if (urlPrefix == null || !urlPrefix.endsWith("/")) {
            throw new IllegalArgumentException("jg4seasons.audio.url-prefix는 /로 끝나야 한다: " + urlPrefix);
        }
    }

    /** file_key가 "/"로 시작하면 "//audio/…"(다른 호스트로 해석)가 되므로 데이터 오류 */
    public String urlOf(String fileKey) {
        if (fileKey.startsWith("/")) {
            throw new IllegalStateException("file_key는 /로 시작하지 않는다: " + fileKey);
        }
        return urlPrefix + fileKey;
    }
}
