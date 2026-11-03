package com.jglearning.jg4seasons.infra.web.error;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

/**
 * 모든 오류를 Problem Details + code·requestId로 응답한다(ADR-17).
 *
 * <p>프레임워크 예외(검증·없는 경로·메서드·형식)는 HTTP 상태로 공통 code를 고른다. 기능 코드는 {@link ApiException}을 던진다. 5xx는 응답에 고정 문구만
 * 넣고 예외는 요청 ID와 함께 ERROR 로그로, 4xx는 {@link ErrorLogs#clientError}로 남긴다. 400 VALIDATION은 언제나 칸별 errors[]를 싣는다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    static final String INTERNAL_DETAIL = "잠시 후 다시 시도해 주세요";
    private static final String BODY = "body";
    private static final String TYPE_MISMATCH = "형식이 맞지 않아요";
    private static final String REQUIRED = "필수 항목이에요";

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> handleApiException(ApiException ex, HttpServletRequest request) {
        ProblemDetail problem = Problems.of(ex.errorCode(), ex.detail(), request);
        ex.extensions().forEach(problem::setProperty);
        return ResponseEntity.status(ex.errorCode().status()).body(problem);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        ErrorLogs.serverError(CommonErrorCode.INTERNAL, request, ex);
        ProblemDetail problem = Problems.of(CommonErrorCode.INTERNAL, INTERNAL_DETAIL, request);
        return ResponseEntity.status(CommonErrorCode.INTERNAL.status()).body(problem);
    }

    /** 본문·폼 검증 실패. 타입 변환 실패(binding failure)는 내부 문구 대신 고정 문구, 객체 단위 오류의 칸은 body */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = new ArrayList<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            String message = error.isBindingFailure() ? TYPE_MISMATCH : error.getDefaultMessage();
            errors.add(fieldError(error.getField(), message));
        }
        ex.getBindingResult()
                .getGlobalErrors()
                .forEach(error -> errors.add(fieldError(BODY, error.getDefaultMessage())));
        return validation(errors, headers, request, ex);
    }

    /**
     * 요청 JSON을 읽지 못함(깨진 JSON, 타입 불일치, 명세에 없는 필드, 뒤에 붙은 값): 400 VALIDATION. Jackson 예외의 경로로 문제 칸을 알리고, 경로가
     * 없으면(문법 오류) field는 body.
     */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        DatabindException databind = findCause(ex, DatabindException.class);
        String field = databind != null ? pathOf(databind) : BODY;
        String message;
        if (findCause(ex, UnrecognizedPropertyException.class) != null) {
            message = "명세에 없는 항목이에요";
        } else if (databind != null && !BODY.equals(field)) {
            message = TYPE_MISMATCH;
        } else {
            message = "JSON 형식이 올바르지 않아요";
        }
        return validation(List.of(fieldError(field, message)), headers, request, ex);
    }

    /** 경로 변수·쿼리 값의 타입 불일치 */
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String field =
                ex instanceof MethodArgumentTypeMismatchException mismatch ? mismatch.getName() : ex.getPropertyName();
        return validation(List.of(fieldError(field != null ? field : BODY, TYPE_MISMATCH)), headers, request, ex);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        return validation(List.of(fieldError(ex.getParameterName(), REQUIRED)), headers, request, ex);
    }

    /** 경로 변수·쿼리 값에 붙은 제약(@Min 등) 위반 */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (status.is5xxServerError()) {
            return handleExceptionInternal(ex, null, headers, status, request);
        }
        List<Map<String, String>> errors = new ArrayList<>();
        for (ParameterValidationResult result : ex.getParameterValidationResults()) {
            String name = result.getMethodParameter().getParameterName();
            for (MessageSourceResolvable error : result.getResolvableErrors()) {
                errors.add(fieldError(name != null ? name : BODY, error.getDefaultMessage()));
            }
        }
        return validation(errors, headers, request, ex);
    }

    /** 나머지 프레임워크 예외(없는 경로·메서드·형식·응답 변환 실패 등): Spring이 만든 ProblemDetail에 code·title·requestId를 채운다 */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        HttpServletRequest servletRequest = servletRequest(request);
        CommonErrorCode errorCode = CommonErrorCode.forStatus(statusCode.value());
        ProblemDetail problem = body instanceof ProblemDetail existing ? existing : ProblemDetail.forStatus(statusCode);
        if (statusCode.is5xxServerError()) {
            ErrorLogs.serverError(errorCode, servletRequest, ex);
            problem.setDetail(INTERNAL_DETAIL);
        } else {
            ErrorLogs.clientError(errorCode, servletRequest, ex);
            // Spring 기본 detail은 영어라 사용자 문구로 바꾼다
            problem.setDetail(errorCode.title());
        }
        Problems.decorate(problem, errorCode, servletRequest);
        return super.handleExceptionInternal(ex, problem, headers, statusCode, request);
    }

    private ResponseEntity<Object> validation(
            List<Map<String, String>> errors, HttpHeaders headers, WebRequest request, Exception ex) {
        HttpServletRequest servletRequest = servletRequest(request);
        ErrorLogs.clientError(CommonErrorCode.VALIDATION, servletRequest, ex);
        String detail = errors.isEmpty() ? null : errors.get(0).get("message");
        ProblemDetail problem = Problems.of(CommonErrorCode.VALIDATION, detail, servletRequest);
        problem.setProperty(Problems.ERRORS, errors);
        return ResponseEntity.status(CommonErrorCode.VALIDATION.status())
                .headers(headers)
                .body(problem);
    }

    /** Jackson 경로를 `items[0].count` 형태로. 경로가 없으면 body */
    static String pathOf(DatabindException ex) {
        StringBuilder path = new StringBuilder();
        for (JacksonException.Reference reference : ex.getPath()) {
            if (reference.getPropertyName() != null) {
                if (!path.isEmpty()) {
                    path.append('.');
                }
                path.append(reference.getPropertyName());
            } else if (reference.getIndex() >= 0) {
                path.append('[').append(reference.getIndex()).append(']');
            }
        }
        return path.isEmpty() ? BODY : path.toString();
    }

    private static <T extends Throwable> T findCause(Throwable ex, Class<T> type) {
        for (Throwable current = ex; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
        }
        return null;
    }

    static Map<String, String> fieldError(String field, String message) {
        return Map.of("field", field, "message", message != null ? message : "");
    }

    private static HttpServletRequest servletRequest(WebRequest request) {
        return ((NativeWebRequest) request).getNativeRequest(HttpServletRequest.class);
    }
}
