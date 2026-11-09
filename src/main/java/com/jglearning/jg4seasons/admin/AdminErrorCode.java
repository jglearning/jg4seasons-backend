package com.jglearning.jg4seasons.admin;

import com.jglearning.jg4seasons.infra.web.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** 관리자 모듈의 오류 code. API 명세 §0.4 표(이슈 #29에서 추가). */
public enum AdminErrorCode implements ErrorCode {
    /** 퇴원(inactive) 학생은 반에 넣을 수 없다 — PATCH로 되살린 뒤 이동(결정 I·L) */
    STUDENT_INACTIVE(HttpStatus.CONFLICT, "퇴원한 학생이에요");

    private final HttpStatus status;
    private final String title;

    AdminErrorCode(HttpStatus status, String title) {
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
