package com.jglearning.archfixture.clazz;

import com.jglearning.archfixture.listening.FixtureEntity;
import com.jglearning.jg4seasons.infra.persistence.AcademyScopedRepository;
import java.util.List;

/** 정상: 조회는 academyId를 받고, save는 예외. */
public interface ScopedRepository extends AcademyScopedRepository<FixtureEntity, Long> {
    List<FixtureEntity> findByAcademyIdAndName(Long academyId, String name);

    <S extends FixtureEntity> S save(S entity);
}
