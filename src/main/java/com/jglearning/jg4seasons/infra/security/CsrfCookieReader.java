package com.jglearning.jg4seasons.infra.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.DefaultCsrfToken;

/**
 * CSRF 토큰 = 쿠키 `csrf` 값(이중 제출). 헤더 `X-CSRF`가 같아야 상태 변경 요청이 통과한다(API 명세 §0.2).
 *
 * <p>쿠키는 로그인·refresh 응답이 직접 발급한다. 여기서는 읽기만 하고 만들거나 지우지 않는다. 쿠키가 없으면 새 난수를 돌려줘 어떤 헤더와도 맞지 않게 한다.
 */
class CsrfCookieReader implements CsrfTokenRepository {

    static final String COOKIE = "csrf";
    static final String HEADER = "X-CSRF";
    private static final String PARAMETER = "_csrf";

    @Override
    public CsrfToken generateToken(HttpServletRequest request) {
        return new DefaultCsrfToken(HEADER, PARAMETER, UUID.randomUUID().toString());
    }

    @Override
    public void saveToken(CsrfToken token, HttpServletRequest request, HttpServletResponse response) {
        // 쿠키는 인증 응답(AuthCookies)이 발급한다
    }

    @Override
    public CsrfToken loadToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (COOKIE.equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                return new DefaultCsrfToken(HEADER, PARAMETER, cookie.getValue());
            }
        }
        return null;
    }
}
