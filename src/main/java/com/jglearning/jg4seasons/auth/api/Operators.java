package com.jglearning.jg4seasons.auth.api;

/** 운영자(superadmin, 학원 없음 — ADR 0032). 다른 모듈(system)이 쓴다. */
public interface Operators {

    /**
     * 요청한 사람(JWT의 사용자)이 지금도 활성 운영자인지 확인한다. 아니면 401 UNAUTHORIZED. JWT의 role은 발급 때 값이라, 비활성화 뒤에도 남은
     * access(최대 30분)로 시스템 API를 쓰지 못하게 읽기·쓰기 전에 부른다
     */
    void requireActiveOperator(long actorId);
}
