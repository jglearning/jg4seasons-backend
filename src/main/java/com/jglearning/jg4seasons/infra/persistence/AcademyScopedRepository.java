package com.jglearning.jg4seasons.infra.persistence;

import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

/**
 * 학원(테넌트) 범위 데이터의 리포지토리 표시.
 *
 * <p>JpaRepository를 상속하지 않는다. findById·findAll처럼 학원 조건 없는 상속 메서드가 생기지 않게 하려는 것이다.
 * 선언한 메서드는 save·saveAll·saveAndFlush를 빼고 이름이 academyId인 파라미터를 받아야 한다
 * (ArchitectureTest.학원_범위_리포지토리_메서드는_academyId를_받는다).
 */
@NoRepositoryBean
public interface AcademyScopedRepository<T, ID> extends Repository<T, ID> {}
