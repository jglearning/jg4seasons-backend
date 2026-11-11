package com.jglearning.jg4seasons.academy;

import com.jglearning.jg4seasons.infra.web.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** 학원 모듈의 오류 code(API 명세 §7, 이슈 #47). */
public enum AcademyErrorCode implements ErrorCode {
    /** 운영자 학원 만들기: 이미 쓰는 학원 주소(slug) */
    ACADEMY_SLUG_TAKEN(HttpStatus.CONFLICT, "이미 쓰는 학원 주소예요");

    private final HttpStatus status;
    private final String title;

    AcademyErrorCode(HttpStatus status, String title) {
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
