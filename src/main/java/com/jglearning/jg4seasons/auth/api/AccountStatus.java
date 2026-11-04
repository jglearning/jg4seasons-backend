package com.jglearning.jg4seasons.auth.api;

import java.util.Locale;

/** 계정 상태. inactive = 비활성(로그인·refresh 불가, 기록 보존). 값은 소문자. */
public enum AccountStatus {
    ACTIVE,
    INACTIVE;

    /** 요청·응답의 문자열 값(소문자) */
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** 소문자 값 → enum. 모르는 값이나 대소문자가 다른 값은 IllegalArgumentException(기본값으로 바꾸지 않음) */
    public static AccountStatus fromValue(String value) {
        for (AccountStatus candidate : values()) {
            if (candidate.value().equals(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("모르는 값: " + value);
    }
}
