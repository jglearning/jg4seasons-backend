package com.jglearning.archfixture.clazz;

import com.jglearning.jg4seasons.infra.persistence.AcademyScopedRepository;
import java.util.Optional;

/** 위반: 파생 쿼리는 순서로 묶인다. 실제 쿼리는 id = academyId값 AND academyId = id값이 된다. */
public interface WrongOrderRepository extends AcademyScopedRepository<ScopedFixtureEntity, Long> {
    Optional<ScopedFixtureEntity> findByIdAndAcademyId(Long academyId, Long id);
}
