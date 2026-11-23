package com.jglearning.jg4seasons.admin;

/**
 * 명단 가져오기 검사 오류 하나(ADR 0036). field는 요청 안 위치(`classes`, `classes[1]`, `classes[1].students[3].name`), code는 만들기가 실패할
 * 때와 같은 오류 code 이름(VALIDATION·NOT_FOUND는 공통, CLASS_NOT_ACTIVE·LICENSE_*는 반 모듈 — 반 모듈 code는 공개 api가 아니라 이름으로만 든다).
 * message는 화면에 그대로 보일 문구라 입력값은 이름·반 이름까지만 넣는다(개인 코드 없음).
 */
public record RosterIssue(String field, String code, String message) {

    static final String VALIDATION = "VALIDATION";
    static final String NOT_FOUND = "NOT_FOUND";
    static final String CLASS_NOT_ACTIVE = "CLASS_NOT_ACTIVE";
    static final String LICENSE_INACTIVE = "LICENSE_INACTIVE";
    static final String LICENSE_SEATS_FULL = "LICENSE_SEATS_FULL";
}
