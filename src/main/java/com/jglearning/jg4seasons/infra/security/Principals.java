package com.jglearning.jg4seasons.infra.security;

import com.jglearning.jg4seasons.infra.web.error.ApiException;
import com.jglearning.jg4seasons.infra.web.error.CommonErrorCode;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * 인증 주체(access JWT)에서 사용자·학원 id를 읽는다. academyId는 요청 값이 아니라 여기서만 얻는다(API 명세 §0.3). claim 이름은 발급하는 쪽(auth)과 같다.
 */
public final class Principals {

    static final String ACADEMY_CLAIM = "aca";

    private Principals() {}

    public static long userId(Jwt jwt) {
        try {
            return Long.parseLong(jwt.getSubject());
        } catch (RuntimeException e) {
            throw new ApiException(CommonErrorCode.UNAUTHORIZED);
        }
    }

    /** 학원이 없는 토큰(시스템 관리자)은 학원 범위 API를 부를 수 없다 → 401 */
    public static long academyId(Jwt jwt) {
        if (jwt.getClaim(ACADEMY_CLAIM) instanceof Number academyId) {
            return academyId.longValue();
        }
        throw new ApiException(CommonErrorCode.UNAUTHORIZED);
    }
}
