package com.jglearning.jg4seasons.wordtest;

import com.jglearning.jg4seasons.infra.web.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 단어시험 사진 채점의 오류 code(작업 지시 §D-7, 사용자 11/24 변경). 기능 꺼짐 FEATURE_DISABLED·하루 3회 DAILY_LIMIT는 프리토킹과 같이 쓰려고
 * CommonErrorCode로 옮겼다(code 값은 그대로)
 */
public enum WordTestErrorCode implements ErrorCode {
    /** 남의 imageKey, 올라가지 않은 사진, 5MB 초과, JPEG·PNG 아님 */
    INVALID_IMAGE(HttpStatus.BAD_REQUEST, "사진을 다시 올려 주세요");

    private final HttpStatus status;
    private final String title;

    WordTestErrorCode(HttpStatus status, String title) {
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
