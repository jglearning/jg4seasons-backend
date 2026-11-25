package com.jglearning.jg4seasons.talk;

import java.util.Arrays;
import java.util.Optional;

/** 세션 상태(계약 D-0, V15 CHECK). code는 DB·응답 값. 문자열 변환은 저장소와 응답 DTO에서만 */
public enum TalkSessionStatus {
    /** 임시 키를 받음(대화 중이거나 끝냈는데 종료 요청이 오지 않음) */
    CREATED("created"),
    /** 종료 요청을 받아 글자 기록을 저장함 */
    ENDED("ended"),
    /** 종료 요청 없이 끊김(정리 작업 — 2단계) */
    EXPIRED("expired"),
    /** 임시 키 발급 실패(하루 한도에 안 셈) */
    FAILED("failed");

    private final String code;

    TalkSessionStatus(String code) {
        this.code = code;
    }

    /** 대소문자까지 정확히 같은 code만 */
    public static Optional<TalkSessionStatus> ofCode(String code) {
        return Arrays.stream(values())
                .filter(status -> status.code.equals(code))
                .findFirst();
    }

    public String code() {
        return code;
    }
}
