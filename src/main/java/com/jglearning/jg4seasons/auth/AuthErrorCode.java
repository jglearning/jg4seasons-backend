package com.jglearning.jg4seasons.auth;

import com.jglearning.jg4seasons.infra.web.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** 인증 모듈의 오류 code. API 명세 §0.4 표. */
public enum AuthErrorCode implements ErrorCode {
    /** 학생 가입 코드·개인 코드, 교사 아이디·비밀번호 불일치. 어느 쪽이 틀렸는지는 구분하지 않는다(문구는 detail로 학생·교사별) */
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "입력한 정보가 맞지 않아요"),
    /** 가입 코드 확인(1.1)에서 반이 없거나 비활성 */
    INVALID_CODE(HttpStatus.NOT_FOUND, "가입 코드를 찾을 수 없어요"),
    /** (반, IP) 5회 실패 → 10분. 확장 필드 until(해제 시각, UTC) */
    LOCKED(HttpStatus.LOCKED, "잠시 후 다시 시도해 주세요"),
    /** 가입 코드 확인·찍기 과다 */
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "너무 많이 시도했어요");

    private final HttpStatus status;
    private final String title;

    AuthErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String title() {
        return title;
    }
}
