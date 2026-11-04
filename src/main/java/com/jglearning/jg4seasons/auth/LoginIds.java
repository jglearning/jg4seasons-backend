package com.jglearning.jg4seasons.auth;

import com.jglearning.jg4seasons.auth.api.StaffAccounts;
import java.util.Locale;

/** 교사·관리자 로그인 아이디: 3~30자 영문·숫자·.·_·-, 대소문자 구분 없음(소문자로 저장·비교). */
public final class LoginIds {

    /** 요청 형식(앞뒤 공백 허용). 정리는 normalize */
    public static final String REQUEST_REGEXP = StaffAccounts.LOGIN_ID_REQUEST_REGEXP;

    public static final String MESSAGE = StaffAccounts.LOGIN_ID_MESSAGE;

    private LoginIds() {}

    public static String normalize(String raw) {
        return raw.strip().toLowerCase(Locale.ROOT);
    }
}
