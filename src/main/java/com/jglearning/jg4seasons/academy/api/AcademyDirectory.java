package com.jglearning.jg4seasons.academy.api;

import java.util.Optional;

/** 다른 모듈이 쓰는 학원 조회. */
public interface AcademyDirectory {

    /** 학원 이름. 학원은 사용자·반의 FK라 반드시 있다 — 없으면 IllegalStateException */
    String nameOf(long academyId);

    /** 학원이 있고 정지(suspended)가 아니다. 학생 입장·세션 갱신 전에 확인한다 */
    boolean isActive(long academyId);

    /** 상태와 무관하게 찾는다(개발 시드용) */
    Optional<Long> findIdBySlug(String slug);
}
