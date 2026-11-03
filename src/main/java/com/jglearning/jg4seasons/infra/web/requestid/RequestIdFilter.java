package com.jglearning.jg4seasons.infra.web.requestid;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/** 요청마다 요청 ID를 만들어 요청 속성·MDC·응답 헤더에 둔다. 요청이 끝나면 MDC에서 지운다(스레드 재사용 대비). */
public class RequestIdFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestIdFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String id = RequestIds.generate();
        request.setAttribute(RequestIds.ATTRIBUTE, id);
        response.setHeader(RequestIds.HEADER, id);
        MDC.put(RequestIds.MDC_KEY, id);
        try {
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException e) {
            // 이 뒤에 서블릿 컨테이너가 남기는 로그에는 MDC가 이미 비어 있다. 요청 ID와 함께 여기서 남기고 다시 던진다
            log.error("필터 체인에서 예외 {} {} requestId={}", request.getMethod(), request.getRequestURI(), id, e);
            throw e;
        } finally {
            MDC.remove(RequestIds.MDC_KEY);
        }
    }
}
