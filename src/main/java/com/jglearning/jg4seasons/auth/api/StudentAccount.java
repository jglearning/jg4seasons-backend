package com.jglearning.jg4seasons.auth.api;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** 학생 계정. 개인 코드 평문은 없다. codeIssuedAt은 코드가 없으면 null, lastLoginAt은 한 번도 로그인하지 않았으면 null. */
public record StudentAccount(
        long id,
        String name,
        @Nullable String school,
        @Nullable String grade,
        AccountStatus status,
        @Nullable Instant codeIssuedAt,
        @Nullable Instant lastLoginAt) {}
