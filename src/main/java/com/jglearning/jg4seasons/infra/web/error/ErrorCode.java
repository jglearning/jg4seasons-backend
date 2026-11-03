package com.jglearning.jg4seasons.infra.web.error;

import org.springframework.http.HttpStatus;

/**
 * 오류 응답의 code. 모듈마다 이 인터페이스를 구현한 enum을 둔다(예: 인증 모듈의 INVALID_CREDENTIALS·LOCKED).
 *
 * <p>code는 enum 상수 이름 그대로다(대문자·숫자·밑줄). 모든 모듈을 통틀어 겹치지 않아야 하고 상태는 4xx·5xx — ErrorCodeConventionTest가
 * 검사한다. title은 사용자에게 그대로 보이는 한국어 문구다. 프론트는 code로 분기한다.
 */
public interface ErrorCode {

    HttpStatus status();

    String title();

    /** enum 상수 이름. enum이 자동으로 구현한다 */
    String name();

    /** 확장 필드 code 값 = enum 상수 이름 */
    default String code() {
        return name();
    }
}
