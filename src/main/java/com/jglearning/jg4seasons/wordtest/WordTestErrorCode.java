package com.jglearning.jg4seasons.wordtest;

import com.jglearning.jg4seasons.infra.web.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** 단어시험 사진 채점의 오류 code(작업 지시 §D-7, 사용자 11/24 변경) */
public enum WordTestErrorCode implements ErrorCode {
    /** 그 학원에서 기능이 꺼짐(학원 settings features.wordTest). 학생·교사 단어시험 API 전부. 프론트는 이 code면 진입 버튼을 숨긴다 */
    FEATURE_DISABLED(HttpStatus.NOT_FOUND, "단어시험을 쓸 수 없는 학원이에요"),
    /** 학생·회차·영역별 하루(학원 날짜) 3회 초과 */
    DAILY_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "오늘은 이 영역을 더 낼 수 없어요"),
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
