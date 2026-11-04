package com.jglearning.jg4seasons.auth.web.dto;

/** 요청의 가입 코드 형식. 대문자 변환·공백 제거는 서비스에서 한다. */
final class AccessCodeFormat {

    static final String REGEXP = "^\\s*[A-Za-z0-9]{8}\\s*$";
    static final String MESSAGE = "가입 코드는 영문·숫자 8자예요";

    private AccessCodeFormat() {}
}
