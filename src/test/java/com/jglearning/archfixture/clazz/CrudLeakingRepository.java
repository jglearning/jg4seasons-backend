package com.jglearning.archfixture.clazz;

import com.jglearning.jg4seasons.infra.persistence.AcademyScopedRepository;
import org.springframework.data.repository.CrudRepository;

/** 위반: CrudRepository를 함께 상속해 학원 조건 없는 findById·findAll이 생긴다. */
public interface CrudLeakingRepository
        extends AcademyScopedRepository<ScopedFixtureEntity, Long>, CrudRepository<ScopedFixtureEntity, Long> {}
