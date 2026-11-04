package com.jglearning.jg4seasons.auth;

import java.util.Optional;
import org.springframework.data.repository.Repository;

/** 학원 범위 엔티티가 아니다(AuthIdentity 주석). 찾은 user_id는 UserRepository로 학원 범위에서 다시 읽는다. */
public interface AuthIdentityRepository extends Repository<AuthIdentity, Long> {

    Optional<AuthIdentity> findByProviderAndIdentifier(AuthProvider provider, String identifier);

    boolean existsByUserIdAndProvider(Long userId, AuthProvider provider);

    AuthIdentity save(AuthIdentity identity);
}
