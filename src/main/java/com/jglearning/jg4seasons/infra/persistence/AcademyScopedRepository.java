package com.jglearning.jg4seasons.infra.persistence;

import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

/**
 * 학원(테넌트) 범위 데이터의 리포지토리 표시.
 *
 * <p>JpaRepository를 상속하지 않는다. findById·findAll처럼 학원 조건 없는 상속 메서드가 생기지 않게 하려는 것이다.
 * CrudRepository 계열을 함께 상속하지 않는다. save·saveAll·saveAndFlush를 뺀 모든 메서드는 academyId를 첫 파라미터로 받고,
 * 파생 쿼리 이름은 By 바로 뒤가 AcademyId(Or 금지), @Query는 :academyId를 쓴다
 * (ArchitectureTest.학원_범위_리포지토리_메서드는_academyId를_받는다).
 */
@NoRepositoryBean
public interface AcademyScopedRepository<T extends AcademyScoped, ID> extends Repository<T, ID> {}
