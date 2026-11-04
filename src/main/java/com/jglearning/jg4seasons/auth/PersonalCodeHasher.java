package com.jglearning.jg4seasons.auth;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * 개인 코드 저장값 = HMAC-SHA256(pepper, academyId + ":" + code), 소문자 hex 64자(ADR-22).
 *
 * <p>값에 학원 id가 들어가 개인 코드는 학원 안에서 고유하고, auth_identity UNIQUE(provider, identifier)로 한 번에 찾는다. 평문은 받기만 하고
 * 어디에도 남기지 않는다.
 */
@Component
public class PersonalCodeHasher {

    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec key;

    public PersonalCodeHasher(AuthProperties properties) {
        this.key = new SecretKeySpec(properties.personalCodePepper().getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    public String hash(long academyId, String personalCode) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM); // Mac은 스레드 안전하지 않아 호출마다 만든다
            mac.init(key);
            byte[] digest = mac.doFinal((academyId + ":" + personalCode).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256을 쓸 수 없다", e);
        }
    }
}
