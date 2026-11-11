package com.jglearning.jg4seasons.auth.persistence;

import com.jglearning.jg4seasons.auth.UserRole;

/** 로그인 전·refresh 때 학원을 모르는 상태에서 읽은 활성 사용자. academyId는 운영자면 null. */
public record AuthUser(long id, Long academyId, UserRole role, String name) {

    /** 운영자 = superadmin이면서 학원 없음(ADR 0032). 둘 중 하나만 맞는 행(데이터 오류)은 운영자로 보지 않는다 */
    public boolean isOperator() {
        return role == UserRole.SUPERADMIN && academyId == null;
    }
}
