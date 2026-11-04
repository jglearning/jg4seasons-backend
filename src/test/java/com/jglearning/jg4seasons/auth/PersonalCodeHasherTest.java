package com.jglearning.jg4seasons.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/** 개인 코드 저장값 = HMAC-SHA256(pepper, academyId:code) 소문자 hex(ADR-22). */
class PersonalCodeHasherTest {

    static final String PEPPER = "test-pepper-0123456789abcdef0123456789";

    final PersonalCodeHasher hasher = new PersonalCodeHasher(
            new AuthProperties(PEPPER, "test-jwt-secret-0123456789abcdef0123", false, null, Duration.ofSeconds(10)));

    @Test
    void 알려진_값과_같다() {
        // 기댓값은 파이썬 hmac으로 따로 계산했다: hmac.new(pepper, b"1:1001", sha256).hexdigest()
        assertThat(hasher.hash(1, "1001"))
                .isEqualTo("fc6c1936c93dc6d13b9a3cd1752a8d865c46223ecf17f1cb88fa3f7fa3b5f5a4");
    }

    @Test
    void 같은_학원_같은_코드는_같은_값이다() {
        assertThat(hasher.hash(7, "3817")).isEqualTo(hasher.hash(7, "3817"));
    }

    @Test
    void 학원이_다르면_다른_값이다() {
        assertThat(hasher.hash(2, "1001"))
                .isEqualTo("96045016c82cfc3d798880c42cf8ed7af4cfedf8de318955eb06aceeeac267fb")
                .isNotEqualTo(hasher.hash(1, "1001"));
    }

    @Test
    void 영으로_시작하는_코드도_그대로_해시한다() {
        assertThat(hasher.hash(1, "0423"))
                .isEqualTo("fb0526bb569c5e66bb6f4c2749e9f3b253b39acbe7a4460d6052e858042f37bd")
                .isNotEqualTo(hasher.hash(1, "423"));
    }

    @Test
    void 저장값은_64자_소문자_hex다() {
        assertThat(hasher.hash(1, "9999")).matches("^[0-9a-f]{64}$");
    }
}
