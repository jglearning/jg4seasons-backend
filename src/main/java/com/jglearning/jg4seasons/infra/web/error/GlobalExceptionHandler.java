package com.jglearning.jg4seasons.infra.web.error;

import com.jglearning.jg4seasons.infra.web.requestid.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * 모든 오류를 Problem Details + code·requestId로 응답한다(API 명세 §0.4, ADR-17).
 *
 * <p>프레임워크 예외(검증·없는 경로·메서드·형식)는 HTTP 상태로 공통 code를 고른다. 기능 코드는 {@link ApiException}을 던진다. 그 밖의 예외는 500
 * INTERNAL — 응답에는 고정 문구만, 예외는 요청 ID와 함께 로그에 남긴다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String INTERNAL_DETAIL = "잠시 후 다시 시도해 주세요";

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> handleApiException(ApiException ex, HttpServletRequest request) {
        ProblemDetail problem = Problems.of(ex.errorCode(), ex.detail(), request);
        ex.extensions().forEach(problem::setProperty);
        return ResponseEntity.status(ex.errorCode().status()).body(problem);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("처리하지 못한 예외 requestId={}", RequestIds.current(request), ex);
        ProblemDetail problem = Problems.of(CommonErrorCode.INTERNAL, INTERNAL_DETAIL, request);
        return ResponseEntity.status(CommonErrorCode.INTERNAL.status()).body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = new ArrayList<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.add(fieldError(error.getField(), error.getDefaultMessage()));
        }
        ex.getBindingResult()
                .getGlobalErrors()
                .forEach(error -> errors.add(fieldError(error.getObjectName(), error.getDefaultMessage())));
        String detail = errors.isEmpty() ? null : errors.get(0).get("message");
        ProblemDetail problem = Problems.of(CommonErrorCode.VALIDATION, detail, servletRequest(request));
        problem.setProperty(Problems.ERRORS, errors);
        return ResponseEntity.status(CommonErrorCode.VALIDATION.status())
                .headers(headers)
                .body(problem);
    }

    /** 나머지 프레임워크 예외(없는 경로·메서드·형식·읽기 실패 등): Spring이 만든 ProblemDetail에 code·title·requestId를 채운다 */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        CommonErrorCode errorCode = CommonErrorCode.forStatus(statusCode.value());
        ProblemDetail problem = body instanceof ProblemDetail existing ? existing : ProblemDetail.forStatus(statusCode);
        // Spring 기본 detail은 영어라 사용자 문구로 바꾼다
        problem.setDetail(errorCode.title());
        Problems.decorate(problem, errorCode, servletRequest(request));
        return super.handleExceptionInternal(ex, problem, headers, statusCode, request);
    }

    static Map<String, String> fieldError(String field, String message) {
        return Map.of("field", field, "message", message != null ? message : "");
    }

    private static HttpServletRequest servletRequest(WebRequest request) {
        return ((NativeWebRequest) request).getNativeRequest(HttpServletRequest.class);
    }
}
