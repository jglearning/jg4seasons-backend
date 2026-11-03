package com.jglearning.jg4seasons.infra.web.error;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.URISyntaxException;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * MVC 밖 오류(방화벽이 거부한 URL, 필터 예외)는 서블릿 ERROR 디스패치로 /error에 온다. Spring Boot 기본 오류 JSON 대신 같은 Problem
 * Details로 응답한다. 원래 상태·경로를 유지하고, 오류 정보 없이 직접 부르면 404.
 *
 * <p>/error는 Security에서 열어 둔다 — 막혀 있으면 원래 400·500이 401 "로그인이 필요해요"로 바뀌어 나갔다.
 */
@Hidden
@RestController
public class ProblemErrorController implements ErrorController {

    private static final int NOT_FOUND = 404;

    @RequestMapping("/error")
    public ResponseEntity<ProblemDetail> error(HttpServletRequest request) {
        int status =
                request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE) instanceof Integer code ? code : NOT_FOUND;
        Throwable cause = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION) instanceof Throwable t ? t : null;
        CommonErrorCode errorCode = CommonErrorCode.forStatus(status);
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatusCode.valueOf(status));
        if (status >= 500) {
            ErrorLogs.serverError(errorCode, request, cause);
            problem.setDetail(GlobalExceptionHandler.INTERNAL_DETAIL);
        } else {
            ErrorLogs.clientError(errorCode, request, cause);
            problem.setDetail(errorCode.title());
        }
        Problems.decorate(problem, errorCode, request);
        if (request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI) instanceof String original) {
            problem.setInstance(pathUri(original));
        }
        return ResponseEntity.status(status).body(problem);
    }

    /** 원래 경로를 URI로(`;`·`//` 같은 문자도 경로로 취급해 인코딩). 바꿀 수 없으면 /error */
    private static URI pathUri(String path) {
        try {
            return new URI(null, null, path, null);
        } catch (URISyntaxException e) {
            return URI.create("/error");
        }
    }
}
