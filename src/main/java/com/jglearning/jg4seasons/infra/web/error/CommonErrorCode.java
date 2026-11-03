package com.jglearning.jg4seasons.infra.web.error;

import org.springframework.http.HttpStatus;

/** 모듈과 무관한 공통 code. API 명세 §0.4 표. */
public enum CommonErrorCode implements ErrorCode {
    VALIDATION(HttpStatus.BAD_REQUEST, "입력을 확인해 주세요"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요해요"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없어요"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "찾을 수 없어요"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청이에요"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 형식이에요"),
    INTERNAL(HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 오류가 생겼어요");

    private final HttpStatus status;
    private final String title;

    CommonErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public String title() {
        return title;
    }

    /** 프레임워크 예외(검증·없는 경로·메서드·형식 등)의 HTTP 상태에 맞는 code. 목록에 없는 4xx는 VALIDATION, 5xx는 INTERNAL */
    static CommonErrorCode forStatus(int status) {
        for (CommonErrorCode code : values()) {
            if (code.status.value() == status) {
                return code;
            }
        }
        return status >= 500 ? INTERNAL : VALIDATION;
    }
}
