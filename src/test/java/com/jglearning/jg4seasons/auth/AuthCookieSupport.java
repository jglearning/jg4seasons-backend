package com.jglearning.jg4seasons.auth;

import com.jglearning.jg4seasons.auth.token.JwtIssuer;
import jakarta.servlet.http.Cookie;
import org.springframework.stereotype.Component;

/** 테스트에서 학생 쿠키(access JWT + csrf)를 만든다. 실제 발급기(JwtIssuer)를 쓴다. */
@Component
public class AuthCookieSupport {

    public static final String CSRF_HEADER = "X-CSRF";
    public static final String CSRF_VALUE = "test-csrf-value";

    private final JwtIssuer issuer;

    public AuthCookieSupport(JwtIssuer issuer) {
        this.issuer = issuer;
    }

    public Cookie access(long userId, UserRole role, Long academyId) {
        return new Cookie("access", issuer.issueAccess(userId, role, academyId));
    }

    public Cookie studentAccess(long userId, long academyId) {
        return access(userId, UserRole.STUDENT, academyId);
    }

    /** 운영자 access — aca 없음(ADR 0032) */
    public Cookie operatorAccess(long userId) {
        return access(userId, UserRole.SUPERADMIN, null);
    }

    public static Cookie csrf() {
        return new Cookie("csrf", CSRF_VALUE);
    }
}
