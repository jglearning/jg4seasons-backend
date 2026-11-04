package com.jglearning.jg4seasons.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordConfigTest {

    final PasswordEncoder encoder = new PasswordConfig().passwordEncoder();

    @Test
    void BCrypt_비용_10으로_해시한다() {
        String hash = encoder.encode("teacher-password");

        assertThat(hash).startsWith("$2a$10$").hasSize(60).doesNotContain("teacher-password");
        assertThat(encoder.matches("teacher-password", hash)).isTrue();
        assertThat(encoder.matches("other-password", hash)).isFalse();
    }

    @Test
    void 더미_해시는_어떤_비밀번호와도_맞지_않는_BCrypt_값이다() {
        assertThat(PasswordConfig.DUMMY_HASH).startsWith("$2a$10$").hasSize(60);
        assertThat(encoder.matches("", PasswordConfig.DUMMY_HASH)).isFalse();
        assertThat(encoder.matches("password", PasswordConfig.DUMMY_HASH)).isFalse();
    }
}
