package com.jglearning.jg4seasons.auth.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.auth.AuthProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/** 찍기 방지·요청 한도의 IP. 설정한 헤더만 믿는다(위조 방지). */
class ClientIpResolverTest {

    static ClientIpResolver resolver(String header) {
        return new ClientIpResolver(new AuthProperties(
                "test-pepper-0123456789abcdef0123456789",
                "test-jwt-secret-0123456789abcdef0123",
                true,
                header,
                Duration.ofSeconds(10)));
    }

    static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.5");
        return request;
    }

    @Test
    void 설정_헤더가_없으면_연결_주소를_쓴다() {
        assertThat(resolver(null).resolve(request())).isEqualTo("10.0.0.5");
    }

    @Test
    void 설정_헤더가_없으면_X_Forwarded_For를_믿지_않는다() {
        MockHttpServletRequest request = request();
        request.addHeader("X-Forwarded-For", "203.0.113.7");

        assertThat(resolver(null).resolve(request)).isEqualTo("10.0.0.5");
    }

    @Test
    void 설정_헤더의_값을_쓴다() {
        MockHttpServletRequest request = request();
        request.addHeader("X-Real-IP", "203.0.113.7");

        assertThat(resolver("X-Real-IP").resolve(request)).isEqualTo("203.0.113.7");
    }

    @Test
    void 쉼표가_있으면_첫_값이고_IPv4_포트는_뗀다() {
        MockHttpServletRequest request = request();
        request.addHeader("X-Real-IP", " 203.0.113.7:51234 , 10.0.0.1");

        assertThat(resolver("X-Real-IP").resolve(request)).isEqualTo("203.0.113.7");
    }

    @Test
    void 대괄호_IPv6의_포트는_떼고_맨_IPv6는_그대로() {
        MockHttpServletRequest bracketed = request();
        bracketed.addHeader("X-Real-IP", "[2001:db8::1]:443");
        MockHttpServletRequest bare = request();
        bare.addHeader("X-Real-IP", "2001:db8::1");

        assertThat(resolver("X-Real-IP").resolve(bracketed)).isEqualTo("2001:db8::1");
        assertThat(resolver("X-Real-IP").resolve(bare)).isEqualTo("2001:db8::1");
    }

    @Test
    void 설정_헤더가_비어_있으면_연결_주소를_쓴다() {
        MockHttpServletRequest request = request();
        request.addHeader("X-Real-IP", "  ");

        assertThat(resolver("X-Real-IP").resolve(request)).isEqualTo("10.0.0.5");
    }
}
