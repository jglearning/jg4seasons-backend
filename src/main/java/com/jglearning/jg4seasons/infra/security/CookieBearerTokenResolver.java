package com.jglearning.jg4seasons.infra.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;

/**
 * access JWT는 HttpOnly 쿠키 `access`에서만 읽는다(ADR-18). Authorization 헤더는 읽지 않는다.
 *
 * <p>/api/v1/auth/ 아래에서는 읽지 않는다. access 쿠키는 Path=/api라 auth 경로에도 실리는데, 만료되거나 깨진 값이 남아 있으면 Resource
 * Server 필터가 먼저 401을 내서 refresh·로그인 자체가 막힌다.
 */
class CookieBearerTokenResolver implements BearerTokenResolver {

    static final String ACCESS_COOKIE = "access";

    @Override
    public String resolve(HttpServletRequest request) {
        if (request.getRequestURI().startsWith(request.getContextPath() + PublicEndpoints.AUTH_PREFIX)) {
            return null;
        }
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (ACCESS_COOKIE.equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
