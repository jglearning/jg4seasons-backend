package com.jglearning.jg4seasons.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** 개인 코드 인증 수단에는 HMAC 값만 들어간다(평문 4자리를 넘기는 실수를 막는다 — ADR-22). */
class AuthIdentityTest {

    static final Instant NOW = Instant.parse("2026-11-05T00:00:00Z");

    @Test
    void 평문_개인_코드는_거부하고_값을_드러내지_않는다() {
        assertThatThrownBy(() -> AuthIdentity.personalCode(1L, "3817", NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .message()
                .doesNotContain("3817");
    }

    @Test
    void 대문자_hex나_길이가_다른_값도_거부한다() {
        assertThatThrownBy(() -> AuthIdentity.personalCode(1L, "A".repeat(64), NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AuthIdentity.personalCode(1L, "a".repeat(63), NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void HMAC_저장값은_받는다() {
        String digest = "fc6c1936c93dc6d13b9a3cd1752a8d865c46223ecf17f1cb88fa3f7fa3b5f5a4";

        assertThat(AuthIdentity.personalCode(1L, digest, NOW).getProvider()).isEqualTo(AuthProvider.PERSONAL_CODE);
    }
}
