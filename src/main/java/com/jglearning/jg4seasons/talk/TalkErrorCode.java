package com.jglearning.jg4seasons.talk;

import com.jglearning.jg4seasons.infra.web.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** 프리토킹 오류 code(계약 D-5). 기능 꺼짐·하루 한도는 공통(FEATURE_DISABLED·DAILY_LIMIT), 사용 기간은 clazz의 LICENSE_INACTIVE */
public enum TalkErrorCode implements ErrorCode {
    /** 구독 없음(1단계는 학원 settings features.talkEntitled). 세션 생성만 — 설정 조회는 200 entitled false */
    SUBSCRIPTION_REQUIRED(HttpStatus.FORBIDDEN, "구독하면 쓸 수 있어요"),
    /** 모델 서비스에서 임시 키를 받지 못함(장애·키 오류·발급 꺼짐) */
    REALTIME_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "잠시 뒤에 다시 시도해 주세요");

    private final HttpStatus status;
    private final String title;

    TalkErrorCode(HttpStatus status, String title) {
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
