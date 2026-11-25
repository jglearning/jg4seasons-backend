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
    /** 그 학원에서 기능이 꺼짐(학원 settings features.*). 프론트는 이 code면 진입 버튼을 숨긴다. 기능 이름은 detail에 */
    FEATURE_DISABLED(HttpStatus.NOT_FOUND, "이 학원에서 쓸 수 없는 기능이에요"),
    /** 기능별 하루(학원 날짜) 횟수 초과. 몇 번까지인지는 detail에 */
    DAILY_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "오늘은 더 할 수 없어요"),
    INTERNAL(HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 오류가 생겼어요"),
    /** 계약(경로·요청·응답 모양)만 먼저 올리고 동작은 아직 없는 경로. {@link NotImplemented#yet()} */
    NOT_IMPLEMENTED(HttpStatus.NOT_IMPLEMENTED, "아직 준비 중인 기능이에요");

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
    public String title() {
        return title;
    }

    /** 프레임워크 예외(검증·없는 경로·메서드·형식 등)의 HTTP 상태에 맞는 code. 목록에 없는 4xx는 VALIDATION, 5xx는 INTERNAL */
    static CommonErrorCode forStatus(int status) {
        for (CommonErrorCode code : values()) {
            // NOT_IMPLEMENTED는 우리가 일부러 낸 "구현 전" 응답만 — 프레임워크가 낸 501은 진짜 서버 쪽 문제라 INTERNAL
            if (code != NOT_IMPLEMENTED && code.status.value() == status) {
                return code;
            }
        }
        return status >= 500 ? INTERNAL : VALIDATION;
    }
}
