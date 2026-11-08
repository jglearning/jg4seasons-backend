package com.jglearning.jg4seasons.infra.web.error;

/**
 * 계약만 먼저 올린 경로의 핸들러 본문: {@code throw NotImplemented.yet();}. 요청 검증(400)과 접근 규칙(401·403)은 핸들러 전에 돌아 지금도 진짜로
 * 동작하고, 통과한 요청만 501 NOT_IMPLEMENTED를 받는다. 구현할 때 이 줄을 서비스 호출로 바꾼다.
 */
public final class NotImplemented {

    private NotImplemented() {}

    public static ApiException yet() {
        return new ApiException(CommonErrorCode.NOT_IMPLEMENTED);
    }
}
