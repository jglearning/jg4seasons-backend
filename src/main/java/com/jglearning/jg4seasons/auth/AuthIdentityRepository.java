package com.jglearning.jg4seasons.auth;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** 학원 범위 엔티티가 아니다(AuthIdentity 주석). 찾은 user_id는 UserRepository로 학원 범위에서 다시 읽는다. */
public interface AuthIdentityRepository extends Repository<AuthIdentity, Long> {

    Optional<AuthIdentity> findByProviderAndIdentifier(AuthProvider provider, String identifier);

    boolean existsByUserIdAndProvider(Long userId, AuthProvider provider);

    Optional<AuthIdentity> findByUserIdAndProvider(Long userId, AuthProvider provider);

    boolean existsByProviderAndIdentifier(AuthProvider provider, String identifier);

    /** 교사 로그인: 로그인 아이디로 비밀번호 수단을 행 잠금으로 읽는다(같은 계정 동시 실패가 5회를 넘지 못하게) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from AuthIdentity i where i.provider = com.jglearning.jg4seasons.auth.AuthProvider.PASSWORD"
            + " and i.identifier = :loginId")
    Optional<AuthIdentity> findPasswordIdentityForUpdate(@Param("loginId") String loginId);

    List<AuthIdentity> findByUserIdInAndProvider(Collection<Long> userIds, AuthProvider provider);

    AuthIdentity save(AuthIdentity identity);

    AuthIdentity saveAndFlush(AuthIdentity identity);
}
