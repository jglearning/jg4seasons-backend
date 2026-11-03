package com.jglearning.jg4seasons.infra.security;

import com.jglearning.jg4seasons.infra.web.error.CommonErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/** 인증이 없거나 만료된 요청: 401 UNAUTHORIZED. 프론트는 이 code를 받으면 refresh 1회 → 실패 시 입장 화면(API 명세 §0.1) */
@Component
class ProblemAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ProblemResponseWriter writer;

    ProblemAuthenticationEntryPoint(ProblemResponseWriter writer) {
        this.writer = writer;
    }

    @Override
    public void commence(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        writer.write(request, response, CommonErrorCode.UNAUTHORIZED);
    }
}
