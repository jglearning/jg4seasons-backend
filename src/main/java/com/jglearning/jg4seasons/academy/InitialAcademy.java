package com.jglearning.jg4seasons.academy;

/** 검사를 통과한 첫 학원·관리자 값(InitialAcademyProperties.resolve). 아이디는 정리(공백 제거·소문자)된 값, 비밀번호는 평문 — 해시만 저장된다. */
record InitialAcademy(
        String academyName, String academySlug, String adminLoginId, String adminPassword, String adminName) {

    /** 비밀번호가 로그·예외 메시지에 찍히지 않게 */
    @Override
    public String toString() {
        return "InitialAcademy[academyName=" + academyName + ", academySlug=" + academySlug + ", adminLoginId="
                + adminLoginId + ", adminName=" + adminName + "]";
    }
}
