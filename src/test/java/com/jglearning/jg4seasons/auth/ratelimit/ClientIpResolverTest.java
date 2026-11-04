package com.jglearning.jg4seasons.auth.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.MutableClock;
import com.jglearning.jg4seasons.auth.AuthProperties;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;

/** 찍기 방지·요청 한도의 IP. 설정한 헤더만 믿는다(위조 방지). */
@ExtendWith(OutputCaptureExtension.class)
class ClientIpResolverTest {

    final MutableClock clock = new MutableClock(Instant.parse("2026-11-05T00:00:00Z"));

    ClientIpResolver resolver(String header) {
        return new ClientIpResolver(
                clock,
                new AuthProperties(
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
    void IPv4_포트는_뗀다() {
        MockHttpServletRequest request = request();
        request.addHeader("X-Real-IP", " 203.0.113.7:51234 ");

        assertThat(resolver("X-Real-IP").resolve(request)).isEqualTo("203.0.113.7");
    }

    @Test
    void 쉼표_목록이면_클라이언트가_보낼_수_있는_첫_값이_아니라_마지막_값을_쓴다() {
        // 덧붙이는 헤더(X-Forwarded-For 등)의 첫 값은 클라이언트가 위조할 수 있다 → 요청마다 바꿔 잠금을 피함
        MockHttpServletRequest request = request();
        request.addHeader("X-Real-IP", "1.2.3.4, 203.0.113.7");

        assertThat(resolver("X-Real-IP").resolve(request)).isEqualTo("203.0.113.7");
    }

    @Test
    void 빈_항목은_건너뛰고_남는_값이_없으면_연결_주소를_쓴다() {
        MockHttpServletRequest trailing = request();
        trailing.addHeader("X-Real-IP", "203.0.113.7,  ");
        MockHttpServletRequest onlyCommas = request();
        onlyCommas.addHeader("X-Real-IP", " , ,");

        assertThat(resolver("X-Real-IP").resolve(trailing)).isEqualTo("203.0.113.7");
        assertThat(resolver("X-Real-IP").resolve(onlyCommas))
                .as("빈 키로 모두 같은 칸에 세지 않게")
                .isEqualTo("10.0.0.5");
    }

    @Test
    void 설정한_헤더가_요청에_없으면_연결_주소를_쓰고_1분에_한_번_WARN(CapturedOutput output) {
        ClientIpResolver resolver = resolver("X-Real-IP");

        assertThat(resolver.resolve(request())).isEqualTo("10.0.0.5");
        resolver.resolve(request());
        assertThat(output.getOut()).containsOnlyOnce("client ip header missing header=X-Real-IP");

        clock.advance(Duration.ofMinutes(1));
        resolver.resolve(request());
        assertThat(output.getOut().split("client ip header missing", -1))
                .as("1분 뒤 다시 한 번")
                .hasSize(3);
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
