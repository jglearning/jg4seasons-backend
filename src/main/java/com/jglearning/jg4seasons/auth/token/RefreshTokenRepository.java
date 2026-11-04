package com.jglearning.jg4seasons.auth.token;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** 학원 범위 테이블이 아니다(쿠키 값의 해시로만 찾는다). */
interface RefreshTokenRepository extends Repository<RefreshToken, Long> {

    /** 행 잠금(SELECT … FOR UPDATE). 같은 토큰으로 동시에 온 회전 둘 중 하나만 새 토큰을 받게 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RefreshToken t where t.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    /** 그 사용자의 refresh를 모두 끊는다(만료도 지금으로 — 유예 없이 거부) */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = coalesce(t.revokedAt, :now), t.expiresAt = :now"
            + " where t.userId = :userId and t.expiresAt > :now")
    int killAllOf(@Param("userId") long userId, @Param("now") Instant now);

    RefreshToken save(RefreshToken token);
}
