package com.jglearning.jg4seasons.auth;

import java.util.Locale;

/** 요청의 가입 코드 정리. 형식(영숫자 8자)은 요청 DTO가 이미 검사했다. */
final class AccessCodes {

    private AccessCodes() {}

    static String normalize(String raw) {
        return raw.strip().toUpperCase(Locale.ROOT);
    }
}
