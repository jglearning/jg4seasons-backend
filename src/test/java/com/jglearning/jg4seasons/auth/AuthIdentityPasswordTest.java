package com.jglearning.jg4seasons.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** 교사·관리자 비밀번호 인증 수단: BCrypt 해시만, 계정 단위로 5회 실패 → 10분 잠금(02 ERD auth_identity). */
class AuthIdentityPasswordTest {

    static final Instant T = Instant.parse("2026-11-05T00:00:00Z");
    static final String HASH = "$2a$10$" + "a".repeat(53);

    static AuthIdentity identity() {
        return AuthIdentity.password(7L, "teacher01", HASH, T);
    }

    static void wrong(AuthIdentity identity, int times, Instant at) {
        for (int i = 0; i < times; i++) {
            identity.recordWrongPassword(at);
        }
    }

    @Test
    void 네_번_틀리면_안_잠기고_다섯_번째에_10분_잠근다() {
        AuthIdentity identity = identity();
        wrong(identity, 4, T);
        assertThat(identity.lockedAt(T)).isFalse();

        assertThat(identity.recordWrongPassword(T)).contains(T.plus(Duration.ofMinutes(10)));
        assertThat(identity.lockedAt(T.plus(Duration.ofMinutes(10)).minusMillis(1)))
                .isTrue();
        assertThat(identity.lockedAt(T.plus(Duration.ofMinutes(10)))).isFalse();
    }

    @Test
    void 잠금이_끝난_뒤_첫_실패는_1부터_센다() {
        AuthIdentity identity = identity();
        wrong(identity, 5, T);
        Instant after = T.plus(Duration.ofMinutes(10));

        wrong(identity, 4, after);

        assertThat(identity.lockedAt(after)).isFalse();
        assertThat(identity.recordWrongPassword(after)).isPresent();
    }

    @Test
    void 성공하면_실패_횟수가_0이다() {
        AuthIdentity identity = identity();
        wrong(identity, 4, T);

        identity.recordSuccess(T);
        wrong(identity, 4, T);

        assertThat(identity.lockedAt(T)).isFalse();
        assertThat(identity.getLastUsedAt()).isEqualTo(T);
    }

    @Test
    void 잠금_해제_시각은_초를_올려_앞당겨지지_않는다() {
        // DB DATETIME은 소수 초를 반올림한다. 12:00:00.700 + 10분을 그대로 넣으면 12:10:01이 되거나 12:10:00으로 앞당겨질 수 있다
        AuthIdentity identity = identity();
        Instant at = T.plusMillis(300);

        wrong(identity, 4, at);

        assertThat(identity.recordWrongPassword(at)).contains(T.plusSeconds(1).plus(Duration.ofMinutes(10)));
    }

    @Test
    void 비밀번호를_바꾸면_잠금이_풀린다() {
        AuthIdentity identity = identity();
        wrong(identity, 5, T);

        identity.changePassword("$2b$10$" + "b".repeat(53), T);

        assertThat(identity.lockedAt(T)).isFalse();
        assertThat(identity.secretHash()).startsWith("$2b$10$");
    }

    @Test
    void 해시_자리에_평문을_넣으면_거부하고_값을_드러내지_않는다() {
        assertThatThrownBy(() -> AuthIdentity.password(7L, "teacher01", "my-password-1", T))
                .isInstanceOf(IllegalArgumentException.class)
                .message()
                .doesNotContain("my-password-1");
        assertThatThrownBy(() -> identity().changePassword("plain-text-pw", T))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
