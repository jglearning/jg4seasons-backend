package com.jglearning.jg4seasons.infra.web.error;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 기능 코드가 던지는 오류. 응답은 code의 HTTP 상태·title, detail(사용자에게 보이는 한국어 문구, 없으면 title)과 확장 필드로 나간다.
 *
 * <p>detail에는 사용자용 고정 문구만 쓴다. 예외 메시지나 개인 코드 같은 입력값을 넣지 않는다. 예외 자체의 메시지는 code만이라 로그에 문구가 찍히지 않는다.
 * 5xx code면 원인 예외(cause)와 함께 ERROR 로그로 남는다.
 *
 * <p>예: {@code throw new ApiException(AuthErrorCode.LOCKED, "10분 뒤에 다시 시도해 주세요").with("until", until)}
 */
public class ApiException extends RuntimeException {

    /** Problem Details 표준 필드와 공통 확장 필드. 확장 필드 이름으로 쓸 수 없다 */
    static final Set<String> RESERVED = Set.of(
            "type", "title", "status", "detail", "instance", Problems.CODE, Problems.REQUEST_ID, Problems.ERRORS);

    private final ErrorCode errorCode;
    private final String detail;
    private final Map<String, Object> extensions = new LinkedHashMap<>();

    public ApiException(ErrorCode errorCode) {
        this(errorCode, null, null);
    }

    public ApiException(ErrorCode errorCode, String detail) {
        this(errorCode, detail, null);
    }

    public ApiException(ErrorCode errorCode, String detail, Throwable cause) {
        super(errorCode.code(), cause);
        this.errorCode = errorCode;
        this.detail = detail;
    }

    /** 확장 필드 추가(예: LOCKED의 until). 예약어(code·requestId·errors·표준 필드)와 null 값은 거부한다 */
    public ApiException with(String key, Object value) {
        if (RESERVED.contains(key)) {
            throw new IllegalArgumentException("예약된 필드 이름이다: " + key);
        }
        if (value == null) {
            throw new IllegalArgumentException("확장 필드 값이 null이다: " + key);
        }
        extensions.put(key, value);
        return this;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    /** 사용자 문구. null이면 응답 detail은 code의 title */
    public String detail() {
        return detail;
    }

    public Map<String, Object> extensions() {
        return Collections.unmodifiableMap(extensions);
    }
}
