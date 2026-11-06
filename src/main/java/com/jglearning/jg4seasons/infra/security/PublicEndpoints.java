package com.jglearning.jg4seasons.infra.security;

import java.util.List;

/**
 * 로그인 없이 부를 수 있는 API(모두 POST). 이 목록 밖의 경로는 URL 층에서 인증을 요구하고, 역할은 메서드 보안(@RolesAllowed)이 정한다.
 *
 * <p>컨트롤러의 @PermitAll 메서드와 이 목록은 같아야 한다(AccessRuleVerifier가 기동 때 확인).
 */
public final class PublicEndpoints {

    public static final String CODE_VERIFY = "/api/v1/auth/code/verify";
    public static final String STUDENT_LOGIN = "/api/v1/auth/student/login";
    public static final String STAFF_LOGIN = "/api/v1/auth/staff/login";
    public static final String REFRESH = "/api/v1/auth/refresh";
    public static final String LOGOUT = "/api/v1/auth/logout";
    /** 인증은 필요. CSRF만 컨트롤러가 직접 확인(sendBeacon은 헤더를 못 붙여 바디 csrf — CsrfTokens) */
    public static final String LISTENING_FLUSH = "/api/v1/listening/flush";

    /** 인증 없이 허용 */
    public static final List<String> NO_AUTH = List.of(CODE_VERIFY, STUDENT_LOGIN, STAFF_LOGIN, REFRESH, LOGOUT);

    /**
     * Security CSRF 필터 제외: 아직 세션이 없거나(로그인·코드 확인) 쿠키 자체가 자격(refresh). 로그아웃은 검사한다. flush는 제외하되 컨트롤러가 헤더 또는
     * 바디로 직접 확인한다(CsrfTokens)
     */
    public static final List<String> NO_CSRF =
            List.of(CODE_VERIFY, STUDENT_LOGIN, STAFF_LOGIN, REFRESH, LISTENING_FLUSH);

    /** 이 아래에서는 access 쿠키를 읽지 않는다 — 만료·깨진 access가 남아 있어도 로그인·refresh가 막히지 않게 */
    public static final String AUTH_PREFIX = "/api/v1/auth/";

    private PublicEndpoints() {}
}
