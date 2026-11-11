package com.jglearning.jg4seasons.auth;

/** 검사를 마친 운영자 설정값. loginId는 정리(소문자)된 값, password는 평문 — 해시만 저장된다. */
record InitialOperator(String loginId, String password, String name) {

    @Override
    public String toString() {
        return "InitialOperator[loginId=" + loginId + ", name=" + name + "]";
    }
}
