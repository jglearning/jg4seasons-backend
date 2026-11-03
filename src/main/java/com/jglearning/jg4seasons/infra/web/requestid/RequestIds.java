package com.jglearning.jg4seasons.infra.web.requestid;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HexFormat;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 요청 ID. 서버가 요청마다 새로 만들고(클라이언트가 보낸 값은 쓰지 않는다), 응답 헤더·로그(MDC)·오류 응답 requestId에 같은 값을 쓴다.
 */
public final class RequestIds {

    public static final String HEADER = "X-Request-Id";
    public static final String ATTRIBUTE = RequestIds.class.getName();
    public static final String MDC_KEY = "requestId";

    private RequestIds() {}

    /** 이 요청의 요청 ID. 필터를 거치지 않은 요청이면 null */
    public static String current(HttpServletRequest request) {
        return request.getAttribute(ATTRIBUTE) instanceof String id ? id : null;
    }

    /** 16자리 소문자 hex(난수 64비트). 추적용이라 보안 난수일 필요는 없다 */
    static String generate() {
        return HexFormat.of().toHexDigits(ThreadLocalRandom.current().nextLong());
    }
}
