package com.jglearning.jg4seasons.auth.token;

import com.jglearning.jg4seasons.auth.AuthProperties;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * 인증 쿠키 3개(API 명세 §0.1, ADR-18). 모두 SameSite=Lax, Domain 없음(호스트 전용), Secure는 설정값(로컬 http만 끔).
 *
 * <ul>
 *   <li>access: JWT, Path=/api, 30분, HttpOnly
 *   <li>refresh: 난수, Path=/api/v1/auth, 학생 90일·교사 14일, HttpOnly
 *   <li>csrf: 난수, Path=/, refresh와 같은 수명, JS가 읽음(HttpOnly 아님) — 상태 변경 요청의 헤더 X-CSRF 값
 * </ul>
 */
@Component
public class AuthCookies {

    public static final String ACCESS = "access";
    public static final String REFRESH = "refresh";
    public static final String CSRF = "csrf";

    static final String ACCESS_PATH = "/api";
    static final String REFRESH_PATH = "/api/v1/auth";
    static final String CSRF_PATH = "/";

    private final boolean secure;

    @Autowired
    public AuthCookies(AuthProperties properties) {
        this(properties.cookieSecure());
    }

    AuthCookies(boolean secure) {
        this.secure = secure;
    }

    public List<ResponseCookie> issue(IssuedTokens tokens) {
        return List.of(
                cookie(ACCESS, tokens.access(), ACCESS_PATH, SessionTtl.ACCESS, true),
                cookie(REFRESH, tokens.refresh(), REFRESH_PATH, tokens.refreshTtl(), true),
                cookie(CSRF, tokens.csrf(), CSRF_PATH, tokens.refreshTtl(), false));
    }

    public List<ResponseCookie> expireAll() {
        return List.of(
                cookie(ACCESS, "", ACCESS_PATH, Duration.ZERO, true),
                cookie(REFRESH, "", REFRESH_PATH, Duration.ZERO, true),
                cookie(CSRF, "", CSRF_PATH, Duration.ZERO, false));
    }

    private ResponseCookie cookie(String name, String value, String path, Duration maxAge, boolean httpOnly) {
        return ResponseCookie.from(name, value)
                .path(path)
                .maxAge(maxAge)
                .httpOnly(httpOnly)
                .secure(secure)
                .sameSite("Lax")
                .build();
    }
}
