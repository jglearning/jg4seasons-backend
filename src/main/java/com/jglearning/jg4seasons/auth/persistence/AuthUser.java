package com.jglearning.jg4seasons.auth.persistence;

import com.jglearning.jg4seasons.auth.UserRole;

/** 로그인 전·refresh 때 학원을 모르는 상태에서 읽은 활성 사용자. academyId는 시스템 관리자면 null. */
public record AuthUser(long id, Long academyId, UserRole role, String name) {}
