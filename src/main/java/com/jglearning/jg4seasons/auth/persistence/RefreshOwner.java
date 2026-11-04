package com.jglearning.jg4seasons.auth.persistence;

/** refresh 소유자(활성 사용자). academyId는 시스템 관리자면 null. role은 소문자. */
public record RefreshOwner(long id, Long academyId, String role, String name) {}
