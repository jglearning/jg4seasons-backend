package com.jglearning.archfixture.clazz;

import com.jglearning.jg4seasons.infra.persistence.AcademyScopedRepository;
import java.util.List;

/** 위반: academyId = ? OR name = ? 라서 이름만 맞으면 다른 학원 행이 나온다. */
public interface OrLeakRepository extends AcademyScopedRepository<ScopedFixtureEntity, Long> {
    List<ScopedFixtureEntity> findByAcademyIdOrName(Long academyId, String name);
}
