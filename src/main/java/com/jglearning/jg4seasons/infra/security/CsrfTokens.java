package com.jglearning.jg4seasons.infra.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * CSRF 이중 제출 직접 확인 — Security CSRF 필터에서 뺀 경로(flush: sendBeacon은 헤더를 못 붙여 바디로 보냄, API 명세 §0.2·§3.1)만 쓴다. 쿠키
 * csrf와 헤더 X-CSRF(있으면 헤더 우선) 또는 바디 csrf가 같아야 한다. 비교는 상수 시간.
 */
public final class CsrfTokens {

    private CsrfTokens() {}

    public static boolean matches(HttpServletRequest request, String bodyToken) {
        String cookie = cookieValue(request);
        String header = request.getHeader(CsrfCookieReader.HEADER);
        String submitted = header != null ? header : bodyToken;
        if (cookie == null || cookie.isEmpty() || submitted == null) {
            return false;
        }
        return MessageDigest.isEqual(
                cookie.getBytes(StandardCharsets.UTF_8), submitted.getBytes(StandardCharsets.UTF_8));
    }

    private static String cookieValue(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (CsrfCookieReader.COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
