package com.jglearning.jg4seasons.auth;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 인증 설정(jg4seasons.auth.*). 비밀값은 환경 변수로만 넣는다(application.yaml은 자리만).
 *
 * <p>pepper·JWT 키가 없거나 32바이트 미만이면 서버가 뜨지 않는다. 오류 메시지에 값은 넣지 않는다.
 *
 * @param personalCodePepper 개인 코드 HMAC 키(ADR-22). JG4SEASONS_PERSONAL_CODE_PEPPER
 * @param jwtSecret access JWT(HS256) 서명 키. JG4SEASONS_JWT_SECRET
 * @param cookieSecure 쿠키 Secure 속성. 로컬(http)만 끈다
 * @param clientIpHeader 클라이언트 IP를 읽을 헤더(프록시가 채움). 비우면 연결 주소만 믿는다
 * @param refreshReuseGrace 회전된 refresh가 다시 와도 폐기하지 않는 유예(탭 둘의 동시 refresh)
 * @param personalCodeMaxFailures 개인 코드 (반, IP) 실패 한도(기본 5, ADR-22). 교실 하나가 공인 IP 하나라 한 반 전체가 같이 센다 — 행사처럼
 *     한꺼번에 입장할 때만 JG4SEASONS_PERSONAL_CODE_MAX_FAILURES로 늘리고 끝나면 되돌린다
 */
@ConfigurationProperties("jg4seasons.auth")
public record AuthProperties(
        String personalCodePepper,
        String jwtSecret,
        @DefaultValue("true") boolean cookieSecure,
        String clientIpHeader,
        @DefaultValue("10s") Duration refreshReuseGrace,
        @DefaultValue("5") int personalCodeMaxFailures) {

    static final int MIN_SECRET_BYTES = 32;

    public AuthProperties {
        requireSecret("jg4seasons.auth.personal-code-pepper", personalCodePepper);
        requireSecret("jg4seasons.auth.jwt-secret", jwtSecret);
        if (clientIpHeader != null && clientIpHeader.isBlank()) {
            clientIpHeader = null;
        }
        if (refreshReuseGrace == null || refreshReuseGrace.isNegative()) {
            throw new IllegalArgumentException("jg4seasons.auth.refresh-reuse-grace는 0 이상이어야 한다");
        }
        if (personalCodeMaxFailures < 1) {
            throw new IllegalArgumentException("jg4seasons.auth.personal-code-max-failures는 1 이상이어야 한다");
        }
    }

    private static void requireSecret(String name, String value) {
        if (value == null || value.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException(name + "가 없거나 " + MIN_SECRET_BYTES + "바이트 미만이다(환경 변수로 설정)");
        }
    }

    /** 비밀값이 로그·예외 메시지에 찍히지 않게 */
    @Override
    public String toString() {
        return "AuthProperties[cookieSecure=" + cookieSecure + ", clientIpHeader=" + clientIpHeader
                + ", refreshReuseGrace=" + refreshReuseGrace + ", personalCodeMaxFailures=" + personalCodeMaxFailures
                + "]";
    }
}
