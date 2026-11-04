package com.jglearning.jg4seasons.auth.token;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

/** 쿠키 3개의 이름·경로·수명·속성(API 명세 §0.1, ADR-18). */
class AuthCookiesTest {

    static final IssuedTokens TOKENS =
            new IssuedTokens("jwt-value", "refresh-value", "csrf-value", Duration.ofDays(90));

    static Map<String, ResponseCookie> byName(List<ResponseCookie> cookies) {
        return cookies.stream().collect(Collectors.toMap(ResponseCookie::getName, Function.identity()));
    }

    @Test
    void 발급_쿠키_3개의_속성() {
        Map<String, ResponseCookie> cookies = byName(new AuthCookies(true).issue(TOKENS));

        assertThat(cookies).containsOnlyKeys("access", "refresh", "csrf");

        ResponseCookie access = cookies.get("access");
        assertThat(access.getValue()).isEqualTo("jwt-value");
        assertThat(access.getPath()).isEqualTo("/api");
        assertThat(access.getMaxAge()).isEqualTo(Duration.ofSeconds(1800));
        assertThat(access.isHttpOnly()).isTrue();

        ResponseCookie refresh = cookies.get("refresh");
        assertThat(refresh.getValue()).isEqualTo("refresh-value");
        assertThat(refresh.getPath()).isEqualTo("/api/v1/auth");
        assertThat(refresh.getMaxAge()).isEqualTo(Duration.ofSeconds(7_776_000));
        assertThat(refresh.isHttpOnly()).isTrue();

        ResponseCookie csrf = cookies.get("csrf");
        assertThat(csrf.getValue()).isEqualTo("csrf-value");
        assertThat(csrf.getPath()).isEqualTo("/");
        assertThat(csrf.getMaxAge()).isEqualTo(Duration.ofSeconds(7_776_000));
        assertThat(csrf.isHttpOnly()).as("JS가 읽는다").isFalse();

        for (ResponseCookie cookie : cookies.values()) {
            assertThat(cookie.getSameSite()).isEqualTo("Lax");
            assertThat(cookie.isSecure()).isTrue();
            assertThat(cookie.getDomain()).as("호스트 전용").isNull();
        }
    }

    @Test
    void 로컬은_Secure를_끈다() {
        assertThat(new AuthCookies(false).issue(TOKENS)).noneMatch(ResponseCookie::isSecure);
    }

    @Test
    void 만료_쿠키는_같은_경로에_수명_0이다() {
        Map<String, ResponseCookie> expired = byName(new AuthCookies(true).expireAll());

        assertThat(expired).containsOnlyKeys("access", "refresh", "csrf");
        assertThat(expired.get("access").getPath()).isEqualTo("/api");
        assertThat(expired.get("refresh").getPath()).isEqualTo("/api/v1/auth");
        assertThat(expired.get("csrf").getPath()).isEqualTo("/");
        assertThat(expired.values()).allSatisfy(cookie -> {
            assertThat(cookie.getMaxAge()).isEqualTo(Duration.ZERO);
            assertThat(cookie.getValue()).isEmpty();
        });
    }
}
