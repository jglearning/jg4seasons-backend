package com.jglearning.jg4seasons.infra.audio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** CloudFront 사용자 지정 정책 서명 쿠키(ADR 0028). 키는 테스트 중에 만든다(파일로 두지 않음) */
class CloudFrontCookieSignerTest {

    static final String RESOURCE = "https://site.test/audio/*";

    static KeyPair keys;

    @BeforeAll
    static void keys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keys = generator.generateKeyPair();
    }

    private final CloudFrontCookieSigner signer = new CloudFrontCookieSigner("KTESTKEYID", keys.getPrivate(), RESOURCE);

    @Test
    void 정책은_공백_없는_JSON() {
        assertThat(CloudFrontCookieSigner.policy(RESOURCE, 1762770000L))
                .isEqualTo("{\"Statement\":[{\"Resource\":\"https://site.test/audio/*\","
                        + "\"Condition\":{\"DateLessThan\":{\"AWS:EpochTime\":1762770000}}}]}");
    }

    @Test
    void 전체_범위면_정책_Resource는_설정값_그대로() {
        String value = signer.cookies(Instant.ofEpochSecond(1762770000L), AudioScope.all())
                .get("CloudFront-Policy");

        assertThat(new String(decode(value), StandardCharsets.UTF_8))
                .isEqualTo(CloudFrontCookieSigner.policy(RESOURCE, 1762770000L));
    }

    @Test
    void 권_범위면_정책_Resource는_권_코드_대시_별표() {
        String value = signer.cookies(Instant.ofEpochSecond(1762770000L), AudioScope.book("L2-B1"))
                .get("CloudFront-Policy");

        assertThat(new String(decode(value), StandardCharsets.UTF_8))
                .isEqualTo(CloudFrontCookieSigner.policy("https://site.test/audio/L2-B1-*", 1762770000L));
    }

    @Test
    void 권_범위_서명도_그_정책으로_검증된다() throws Exception {
        String value = signer.cookies(Instant.ofEpochSecond(1762770000L), AudioScope.book("K-B1"))
                .get("CloudFront-Signature");
        byte[] policy = CloudFrontCookieSigner.policy("https://site.test/audio/K-B1-*", 1762770000L)
                .getBytes(StandardCharsets.UTF_8);

        assertThat(verify(keys.getPublic(), policy, decode(value))).isTrue();
    }

    @Test
    void 설정_주소가_audio_별표로_끝나지_않으면_만들지_않는다() {
        // 끝 *를 떼고 권 패턴을 붙이므로, 형식이 다르면 …/audioL2-B1-* 같은 깨진 주소를 예외 없이 서명하게 된다
        for (String resource :
                new String[] {"https://site.test/audio/", "https://site.test/other/*", "https://site.test/*"}) {
            assertThatThrownBy(() -> new CloudFrontCookieSigner("KTESTKEYID", keys.getPrivate(), resource))
                    .as(resource)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void 쿠키_셋을_이_순서로() {
        Map<String, String> cookies = signer.cookies(Instant.ofEpochSecond(1762770000L), AudioScope.all());

        assertThat(cookies).containsOnlyKeys("CloudFront-Policy", "CloudFront-Signature", "CloudFront-Key-Pair-Id");
        assertThat(cookies.keySet())
                .containsExactly("CloudFront-Policy", "CloudFront-Signature", "CloudFront-Key-Pair-Id");
        assertThat(cookies).containsEntry("CloudFront-Key-Pair-Id", "KTESTKEYID");
    }

    @Test
    void 정책_쿠키는_정책의_URL_안전_base64() {
        String value = signer.cookies(Instant.ofEpochSecond(1762770000L), AudioScope.all())
                .get("CloudFront-Policy");

        assertThat(value).doesNotContain("+", "=", "/");
        assertThat(new String(decode(value), StandardCharsets.UTF_8))
                .isEqualTo(CloudFrontCookieSigner.policy(RESOURCE, 1762770000L));
    }

    @Test
    void 서명은_공개_키로_검증된다() throws Exception {
        String value = signer.cookies(Instant.ofEpochSecond(1762770000L), AudioScope.all())
                .get("CloudFront-Signature");
        byte[] policy = CloudFrontCookieSigner.policy(RESOURCE, 1762770000L).getBytes(StandardCharsets.UTF_8);

        assertThat(value).doesNotContain("+", "=", "/");
        assertThat(verify(keys.getPublic(), policy, decode(value))).isTrue();
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        assertThat(verify(generator.generateKeyPair().getPublic(), policy, decode(value)))
                .isFalse();
    }

    @Test
    void 만료는_epoch_초() {
        String value = signer.cookies(Instant.ofEpochMilli(1762770000999L), AudioScope.all())
                .get("CloudFront-Policy");

        assertThat(new String(decode(value), StandardCharsets.UTF_8)).contains("\"AWS:EpochTime\":1762770000}");
    }

    @Test
    void base64_개인_키를_읽는다() {
        String base64 = Base64.getEncoder().encodeToString(keys.getPrivate().getEncoded());

        assertThat(CloudFrontCookieSigner.parsePrivateKey(base64)).isEqualTo(keys.getPrivate());
    }

    @Test
    void 개인_키를_못_읽으면_값_없이_거부() {
        assertThatThrownBy(() -> CloudFrontCookieSigner.parsePrivateKey("not-base64!"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("not-base64!")
                .hasNoCause();
        String pem = "-----BEGIN PRIVATE KEY-----MIIEv-----END PRIVATE KEY-----";
        assertThatThrownBy(() -> CloudFrontCookieSigner.parsePrivateKey(pem))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("MIIEv");
        // base64로는 읽히지만 키가 아님
        assertThatThrownBy(() -> CloudFrontCookieSigner.parsePrivateKey("AAAA"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasNoCause();
    }

    /** CloudFront식 URL 안전 base64를 되돌린다 */
    private static byte[] decode(String value) {
        return Base64.getDecoder()
                .decode(value.replace('-', '+').replace('_', '=').replace('~', '/'));
    }

    private static boolean verify(PublicKey key, byte[] data, byte[] signature) throws Exception {
        Signature verifier = Signature.getInstance("SHA1withRSA");
        verifier.initVerify(key);
        verifier.update(data);
        return verifier.verify(signature);
    }
}
