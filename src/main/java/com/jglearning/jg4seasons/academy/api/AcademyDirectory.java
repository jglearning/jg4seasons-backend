package com.jglearning.jg4seasons.academy.api;

import java.util.Optional;

/** 다른 모듈이 쓰는 학원 조회. */
public interface AcademyDirectory {

    /** 학원 이름. 학원은 사용자·반의 FK라 반드시 있다 — 없으면 IllegalStateException */
    String nameOf(long academyId);

    Optional<Long> findIdBySlug(String slug);
}
