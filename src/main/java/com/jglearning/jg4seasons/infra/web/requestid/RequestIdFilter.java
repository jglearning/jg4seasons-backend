package com.jglearning.jg4seasons.infra.web.requestid;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/** 요청마다 요청 ID를 만들어 요청 속성·MDC·응답 헤더에 둔다. 요청이 끝나면 MDC에서 지운다(스레드 재사용 대비). */
public class RequestIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String id = RequestIds.generate();
        request.setAttribute(RequestIds.ATTRIBUTE, id);
        response.setHeader(RequestIds.HEADER, id);
        MDC.put(RequestIds.MDC_KEY, id);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(RequestIds.MDC_KEY);
        }
    }
}
