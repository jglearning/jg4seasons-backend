package com.jglearning.jg4seasons.auth.api;

/** 발급·재발급한 개인 코드 평문(ADR-22). 응답에 한 번 싣고 버린다 — 저장·로그 금지. */
public record IssuedCode(long studentId, String name, String personalCode) {

    /** 로그에 개인 코드가 남지 않게 */
    @Override
    public String toString() {
        return "IssuedCode[studentId=" + studentId + "]";
    }
}
