package com.jglearning.jg4seasons.infra.web.error;

/**
 * 계약만 먼저 올린 경로의 핸들러 본문: {@code throw NotImplemented.yet();}. 구현할 때 이 줄을 서비스 호출로 바꾼다.
 *
 * <p>응답 순서: 인증(401) → 요청 본문·경로 값 검증(400) → 역할(@RolesAllowed, 403) → 핸들러 안 검사(400) → 501. 검증이 역할보다 먼저라 역할이
 * 맞지 않는 사용자가 틀린 입력을 보내면 403이 아니라 400을 받는다(칸 이름만 드러나고 데이터는 아니다 — 기존 경로도 같은 순서, issues.md).
 */
public final class NotImplemented {

    private NotImplemented() {}

    public static ApiException yet() {
        return new ApiException(CommonErrorCode.NOT_IMPLEMENTED);
    }
}
