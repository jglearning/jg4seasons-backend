package com.jglearning.jg4seasons.infra.web.error;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 기능 코드가 던지는 오류. 응답은 code의 HTTP 상태·title, 이 예외의 detail(사용자에게 보이는 한국어 문구)과 확장 필드로 나간다.
 *
 * <p>예: {@code throw new ApiException(AuthErrorCode.LOCKED, "10분 뒤에 다시 시도해 주세요").with("until", until)}
 */
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String detail;
    private final Map<String, Object> extensions = new LinkedHashMap<>();

    public ApiException(ErrorCode errorCode, String detail) {
        super(errorCode.code() + ": " + detail);
        this.errorCode = errorCode;
        this.detail = detail;
    }

    /** 확장 필드 추가(예: LOCKED의 until). code·requestId·errors는 예약어라 덮어쓰지 않는다 */
    public ApiException with(String key, Object value) {
        extensions.put(key, value);
        return this;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public String detail() {
        return detail;
    }

    public Map<String, Object> extensions() {
        return Collections.unmodifiableMap(extensions);
    }
}
