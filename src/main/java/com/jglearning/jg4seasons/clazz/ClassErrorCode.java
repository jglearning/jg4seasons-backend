package com.jglearning.jg4seasons.clazz;

import com.jglearning.jg4seasons.infra.web.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** 반 모듈의 오류 code. API 명세 §0.4 표(이슈 #29에서 추가). */
public enum ClassErrorCode implements ErrorCode {
    /** 학생 등록·이동 대상 반이 진행 중(active)이 아니다(결정 I) */
    CLASS_NOT_ACTIVE(HttpStatus.CONFLICT, "진행 중인 반이 아니에요"),
    /** 이동: 학생이 이미 그 반에만 소속이다(결정 I) */
    ALREADY_IN_CLASS(HttpStatus.CONFLICT, "이미 그 반 학생이에요");

    private final HttpStatus status;
    private final String title;

    ClassErrorCode(HttpStatus status, String title) {
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
