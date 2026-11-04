package com.jglearning.jg4seasons.auth;

import java.util.Locale;

/** 교사·관리자 로그인 아이디: 3~30자 영문·숫자·.·_·-, 대소문자 구분 없음(소문자로 저장·비교). */
public final class LoginIds {

    /** 요청 형식(앞뒤 공백 허용). 정리는 normalize */
    public static final String REQUEST_REGEXP = "^\\s*[A-Za-z0-9._-]{3,30}\\s*$";

    public static final String MESSAGE = "아이디는 영문·숫자·.·_·- 3~30자예요";

    private LoginIds() {}

    public static String normalize(String raw) {
        return raw.strip().toLowerCase(Locale.ROOT);
    }
}
