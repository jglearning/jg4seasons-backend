package com.jglearning.jg4seasons.clazz.api;

/**
 * 진도표 행(class_session) 상태. value = DB·API 값. cancelled = 휴강(행은 남는다 — ADR 0030). DONE은 저장하지 않는다 — 응답 때
 * SessionStatusRule.shown이 날짜로 계산(이슈 #45). 저장 상태로 done을 판정하지 말 것(늘 빈 결과).
 */
public enum SessionStatus {
    SCHEDULED("scheduled"),
    DONE("done"),
    CANCELLED("cancelled");

    private final String value;

    SessionStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    /** DB 값. 모르는 값이면 데이터 오류 */
    public static SessionStatus fromValue(String value) {
        for (SessionStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalStateException("class_session.status 값이 아니다: " + value);
    }
}
