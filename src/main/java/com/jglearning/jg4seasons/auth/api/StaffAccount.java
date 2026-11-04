package com.jglearning.jg4seasons.auth.api;

import java.time.Instant;

/**
 * 교사·관리자 계정. 비밀번호·해시는 없다.
 *
 * @param loginId 로그인 아이디(정리된 소문자). 비밀번호 수단이 아직 없으면 null
 * @param lastLoginAt 한 번도 로그인하지 않았으면 null
 */
public record StaffAccount(
        long id, String name, String loginId, StaffRole role, AccountStatus status, Instant lastLoginAt) {}
