package com.jglearning.jg4seasons.infra.web.error;

import org.springframework.http.HttpStatus;

/**
 * 오류 응답의 code(API 명세 §0.4). 모듈마다 이 인터페이스를 구현한 enum을 둔다(예: 인증 모듈의 INVALID_CREDENTIALS·LOCKED).
 *
 * <p>title은 사용자에게 그대로 보이는 한국어 문구다. 프론트는 code로 분기한다.
 */
public interface ErrorCode {

    HttpStatus status();

    /** 확장 필드 code 값. 대문자와 밑줄(예: INVALID_CREDENTIALS) */
    String code();

    String title();
}
