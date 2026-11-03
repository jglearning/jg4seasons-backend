package com.jglearning.archfixture.clazz;

import com.jglearning.jg4seasons.infra.persistence.AcademyScopedRepository;
import java.util.List;

/** 위반: 학원 범위 리포지토리에 academyId 없는 조회가 있다. */
public interface UnscopedRepository extends AcademyScopedRepository<ScopedFixtureEntity, Long> {
    List<ScopedFixtureEntity> findByName(String name);
}
