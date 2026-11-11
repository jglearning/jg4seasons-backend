package com.jglearning.jg4seasons.infra.audio;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * CloudFront 서명 쿠키 값을 만든다(ADR 0028). 사용자 지정 정책 하나(Resource + 만료)를 RSA 개인 키로 서명한다. 외부 호출 없이 서버 안에서 계산한다.
 *
 * <p>CloudFront 규칙: 정책은 공백 없는 JSON, 서명은 SHA1withRSA, 두 값 모두 base64 뒤 {@code + = /}를 {@code - _ ~}로 바꾼다.
 *
 * <p>같은 패키지에서만 만든다 — 값 검사는 {@link AudioSigningProperties}가 하고 {@link AudioConfig}만 이 클래스를 만든다.
 */
final class CloudFrontCookieSigner {

    static final String POLICY = "CloudFront-Policy";
    static final String SIGNATURE = "CloudFront-Signature";
    static final String KEY_PAIR_ID = "CloudFront-Key-Pair-Id";

    /** {@link #cookies}가 내는 쿠키 이름 전부 — 로그아웃 때 지울 목록도 이것을 쓴다 */
    static final Set<String> NAMES = Set.of(POLICY, SIGNATURE, KEY_PAIR_ID);

    private final String keyPairId;
    private final PrivateKey privateKey;
    private static final String AUDIO_ALL = "/audio/*";

    private final String resource;
    /** resource에서 끝 *를 뗀 것(https://<호스트>/audio/) — 권 범위는 여기에 <권 코드>-* */
    private final String audioPrefix;

    /** resource는 https://<호스트>/audio/* — 아니면 IllegalArgumentException(권 범위가 깨진 주소가 되어 CloudFront가 로그 없이 403) */
    CloudFrontCookieSigner(String keyPairId, PrivateKey privateKey, String resource) {
        if (resource == null || !resource.endsWith(AUDIO_ALL)) {
            throw new IllegalArgumentException("음원 서명 Resource는 /audio/*로 끝나야 한다");
        }
        this.keyPairId = keyPairId;
        this.privateKey = privateKey;
        this.resource = resource;
        this.audioPrefix = resource.substring(0, resource.length() - 1);
    }

    /** 쿠키 이름 → 값, Policy·Signature·Key-Pair-Id 순서. 만료는 epoch 초(밀리초는 버림) */
    Map<String, String> cookies(Instant expires, AudioScope.Signed scope) {
        String policy = policy(resourceOf(scope), expires.getEpochSecond());
        Map<String, String> cookies = new LinkedHashMap<>();
        cookies.put(POLICY, urlSafe(Base64.getEncoder().encodeToString(policy.getBytes(StandardCharsets.UTF_8))));
        cookies.put(SIGNATURE, urlSafe(Base64.getEncoder().encodeToString(sign(policy))));
        cookies.put(KEY_PAIR_ID, keyPairId);
        return Collections.unmodifiableMap(cookies);
    }

    /** 한 줄 base64(PKCS#8 DER) 개인 키. 실패 메시지에는 입력값도 원인 예외도 넣지 않는다(키 조각이 로그에 남지 않게) */
    static PrivateKey parsePrivateKey(String base64Pkcs8Der) {
        try {
            byte[] der = Base64.getDecoder().decode(base64Pkcs8Der.strip());
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (IllegalArgumentException | GeneralSecurityException e) {
            throw new IllegalArgumentException(
                    "jg4seasons.audio.signing.private-key를 읽지 못했다(한 줄 base64 PKCS#8 DER RSA 키여야 한다)");
        }
    }

    /** all → 설정값(https://<호스트>/audio/*) 그대로, book → https://<호스트>/audio/<권 코드>-* */
    private String resourceOf(AudioScope.Signed scope) {
        return switch (scope) {
            case AudioScope.All all -> resource;
            case AudioScope.Book book -> audioPrefix + book.code() + "-*";
        };
    }

    static String policy(String resource, long epochSeconds) {
        return "{\"Statement\":[{\"Resource\":\"" + resource + "\",\"Condition\":{\"DateLessThan\":{\"AWS:EpochTime\":"
                + epochSeconds + "}}}]}";
    }

    static String urlSafe(String base64) {
        return base64.replace('+', '-').replace('=', '_').replace('/', '~');
    }

    private byte[] sign(String policy) {
        try {
            Signature signature = Signature.getInstance("SHA1withRSA");
            signature.initSign(privateKey);
            signature.update(policy.getBytes(StandardCharsets.UTF_8));
            return signature.sign();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("음원 쿠키 서명 실패", e);
        }
    }
}
