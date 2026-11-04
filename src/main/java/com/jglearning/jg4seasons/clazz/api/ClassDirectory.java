package com.jglearning.jg4seasons.clazz.api;

import java.util.Optional;

/** 다른 모듈(auth)이 쓰는 반 조회. */
public interface ClassDirectory {

    /**
     * 가입 코드로 활성 반을 찾는다. 로그인 전이라 학원을 모르는 상태에서 부른다.
     *
     * @param normalizedCode 앞뒤 공백을 없애고 대문자로 바꾼 가입 코드
     */
    Optional<ClassEntry> findActiveByAccessCode(String normalizedCode);

    /** 학생이 이 반에 지금 소속(enrollment.left_at 없음)인지. 반이 그 학원 것이 아니면 false */
    boolean isCurrentMember(long academyId, long classId, long studentId);
}
