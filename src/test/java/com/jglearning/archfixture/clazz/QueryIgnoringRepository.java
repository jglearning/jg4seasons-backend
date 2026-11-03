package com.jglearning.archfixture.clazz;

import com.jglearning.jg4seasons.infra.persistence.AcademyScopedRepository;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;

/** 위반: academyId를 받기만 하고 쿼리에서 쓰지 않는다. */
public interface QueryIgnoringRepository extends AcademyScopedRepository<ScopedFixtureEntity, Long> {
    @Query("select e from ScopedFixtureEntity e where e.id = :id")
    Optional<ScopedFixtureEntity> findOne(Long academyId, Long id);
}
