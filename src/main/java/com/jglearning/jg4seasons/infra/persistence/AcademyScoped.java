package com.jglearning.jg4seasons.infra.persistence;

/**
 * 학원(테넌트) 소유 데이터 표시. 이 인터페이스를 구현한 엔티티는 AcademyScopedRepository로만 다룬다
 * (ArchitectureTest.학원_범위_엔티티는_AcademyScopedRepository로만_다룬다).
 */
public interface AcademyScoped {
    Long getAcademyId();
}
