package com.jglearning.archfixture.errors;

import com.jglearning.jg4seasons.infra.web.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** 위반 예제: 소문자 code, 오류가 아닌 상태(200), 공통 code와 겹치는 이름(NOT_FOUND). */
public enum BadErrorCode implements ErrorCode {
    lower_case(HttpStatus.BAD_REQUEST),
    NOT_ERROR(HttpStatus.OK),
    NOT_FOUND(HttpStatus.NOT_FOUND);

    private final HttpStatus status;

    BadErrorCode(HttpStatus status) {
        this.status = status;
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String title() {
        return "예제";
    }
}
