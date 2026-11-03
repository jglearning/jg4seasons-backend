package com.jglearning.jg4seasons.infra.web;

import com.jglearning.jg4seasons.infra.web.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** 테스트 전용 모듈 오류 code(공통 code 표에 없는 상태). 인증 모듈의 LOCKED·외부 연동 실패와 같은 모양. */
enum ProbeErrorCode implements ErrorCode {
    PROBE_LOCKED(HttpStatus.LOCKED, "잠시 잠겼어요"),
    PROBE_UPSTREAM(HttpStatus.BAD_GATEWAY, "연결이 원활하지 않아요");

    private final HttpStatus status;
    private final String title;

    ProbeErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String title() {
        return title;
    }
}
