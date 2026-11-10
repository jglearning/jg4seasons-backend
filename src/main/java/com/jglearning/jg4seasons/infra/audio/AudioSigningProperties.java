package com.jglearning.jg4seasons.infra.audio;

import java.time.Duration;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 음원 서명 쿠키 설정(jg4seasons.audio.signing.*, ADR 0028). 기본은 꺼짐(로컬·테스트). 비밀값(개인 키)은 환경 변수로만.
 *
 * <p>켰는데 칸이 비었거나 형식이 틀리면 서버가 뜨지 않는다. 오류 메시지에는 칸 이름만 넣는다.
 *
 * @param keyPairId CloudFront 공개 키 ID
 * @param privateKey 한 줄 base64(PKCS#8 DER) RSA 개인 키. JG4SEASONS_AUDIO_SIGNING_PRIVATE_KEY
 * @param resource 정책 Resource. 사이트 호스트를 정확히: {@code https://<호스트>/audio/*}
 * @param ttl 쿠키 수명(정책 만료와 Max-Age)
 */
@ConfigurationProperties("jg4seasons.audio.signing")
public record AudioSigningProperties(
        @DefaultValue("false") boolean enabled,
        String keyPairId,
        String privateKey,
        String resource,
        @DefaultValue("2h") Duration ttl) {

    private static final Pattern KEY_PAIR_ID = Pattern.compile("^[A-Z0-9]+$");
    private static final String AUDIO_RESOURCE_SUFFIX = "/audio/*";

    public AudioSigningProperties {
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("jg4seasons.audio.signing.ttl은 0보다 커야 한다");
        }
        if (enabled) {
            require("key-pair-id", keyPairId);
            // CloudFront 공개 키 ID(K…). 쿠키 값에 못 쓰는 글자(; , 공백)가 섞이면 로그인마다 500이 된다 — 서버가 뜰 때 막는다
            if (!KEY_PAIR_ID.matcher(keyPairId).matches()) {
                throw new IllegalArgumentException("jg4seasons.audio.signing.key-pair-id는 대문자와 숫자만 쓴다");
            }
            require("private-key", privateKey);
            require("resource", resource);
            // 정책 JSON에 그대로 들어간다 — 따옴표·역슬래시는 JSON을 깨뜨린다
            // 쿠키 Path(/audio/)와 짝 — CloudFront는 Resource가 요청 URL과 맞지 않으면 모든 음원을 403으로 막는다(로그 없이)
            if (!resource.startsWith("https://")
                    || !resource.endsWith(AUDIO_RESOURCE_SUFFIX)
                    || resource.length() <= "https://".length() + AUDIO_RESOURCE_SUFFIX.length()
                    || resource.contains("\"")
                    || resource.contains("\\")) {
                throw new IllegalArgumentException(
                        "jg4seasons.audio.signing.resource는 https://<호스트>/audio/* 형식이어야 한다(따옴표·역슬래시 없이)");
            }
        }
    }

    private static void require(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("jg4seasons.audio.signing." + name + "가 비었다(서명을 켰으면 환경 변수로 설정)");
        }
    }

    /** 개인 키가 로그·예외 메시지에 찍히지 않게 */
    @Override
    public String toString() {
        return "AudioSigningProperties[enabled=" + enabled + ", keyPairId=" + keyPairId + ", resource=" + resource
                + ", ttl=" + ttl + "]";
    }
}
