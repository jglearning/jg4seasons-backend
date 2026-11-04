package com.jglearning.jg4seasons.auth;

import com.jglearning.jg4seasons.infra.persistence.AcademyScopedRepository;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends AcademyScopedRepository<User, Long> {

    Optional<User> findByAcademyIdAndId(Long academyId, Long id);

    List<User> findByAcademyIdAndRoleOrderByIdAsc(Long academyId, UserRole role);
}
