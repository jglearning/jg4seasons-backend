package com.jglearning.jg4seasons.infra.web.error;

import com.jglearning.jg4seasons.infra.web.requestid.RequestIds;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 오류 응답 로그. 4xx는 code·메서드·경로·요청 ID·예외 종류만 남긴다 — 예외 메시지에는 입력값(개인 코드 등)이 섞일 수 있어 쓰지 않는다. 401·404는 흔해서
 * INFO, 그 밖의 4xx는 WARN, 5xx는 ERROR + 스택.
 */
public final class ErrorLogs {

    private static final Logger log = LoggerFactory.getLogger(ErrorLogs.class);

    private ErrorLogs() {}

    /** ERROR 디스패치(/error)면 원래 요청 경로 */
    static String path(HttpServletRequest request) {
        return request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI) instanceof String original
                ? original
                : request.getRequestURI();
    }

    public static void clientError(ErrorCode errorCode, HttpServletRequest request, Throwable ex) {
        int status = errorCode.status().value();
        String type = ex != null ? ex.getClass().getSimpleName() : "-";
        if (status == 401 || status == 404) {
            log.info(
                    "{} {} {} requestId={} exception={}",
                    errorCode.code(),
                    request.getMethod(),
                    path(request),
                    RequestIds.current(request),
                    type);
        } else {
            log.warn(
                    "{} {} {} requestId={} exception={}",
                    errorCode.code(),
                    request.getMethod(),
                    path(request),
                    RequestIds.current(request),
                    type);
        }
    }

    public static void serverError(ErrorCode errorCode, HttpServletRequest request, Throwable ex) {
        log.error(
                "{} {} {} requestId={}",
                errorCode.code(),
                request.getMethod(),
                path(request),
                RequestIds.current(request),
                ex);
    }
}
