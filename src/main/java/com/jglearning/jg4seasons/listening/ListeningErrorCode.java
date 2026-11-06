package com.jglearning.jg4seasons.listening;

import com.jglearning.jg4seasons.infra.web.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** 듣기 모듈의 오류 code. API 명세 §0.4 표. */
public enum ListeningErrorCode implements ErrorCode {
    /** 미래 회차(진도표상 수업일 전, 또는 휴강만 있음) */
    LESSON_LOCKED(HttpStatus.LOCKED, "아직 열리지 않은 회차예요");

    private final HttpStatus status;
    private final String title;

    ListeningErrorCode(HttpStatus status, String title) {
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
