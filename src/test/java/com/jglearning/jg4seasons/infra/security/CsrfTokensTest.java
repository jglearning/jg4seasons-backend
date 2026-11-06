package com.jglearning.jg4seasons.infra.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/** flush의 CSRF 직접 확인: 쿠키 csrf = 헤더 X-CSRF(우선) 또는 바디 csrf. */
class CsrfTokensTest {

    static MockHttpServletRequest request(String cookie, String header) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (cookie != null) {
            request.setCookies(new Cookie("csrf", cookie));
        }
        if (header != null) {
            request.addHeader("X-CSRF", header);
        }
        return request;
    }

    @Test
    void 헤더나_바디가_쿠키와_같으면_통과() {
        assertThat(CsrfTokens.matches(request("t", "t"), null)).isTrue();
        assertThat(CsrfTokens.matches(request("t", null), "t")).isTrue();
    }

    @Test
    void 다르거나_없으면_거부() {
        assertThat(CsrfTokens.matches(request("t", "x"), null)).isFalse();
        assertThat(CsrfTokens.matches(request("t", null), "x")).isFalse();
        assertThat(CsrfTokens.matches(request("t", null), null)).isFalse();
        assertThat(CsrfTokens.matches(request(null, "t"), "t")).as("쿠키 없음").isFalse();
        assertThat(CsrfTokens.matches(request("", null), "")).as("빈 쿠키").isFalse();
    }

    @Test
    void 헤더가_있으면_헤더로만_판단() {
        assertThat(CsrfTokens.matches(request("t", "x"), "t")).isFalse();
    }
}
