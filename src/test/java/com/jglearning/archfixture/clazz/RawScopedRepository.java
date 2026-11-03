package com.jglearning.archfixture.clazz;

import org.springframework.data.jpa.repository.JpaRepository;

/** 위반: 학원 범위 엔티티를 일반 JpaRepository로 다룬다. */
public interface RawScopedRepository extends JpaRepository<ScopedFixtureEntity, Long> {}
