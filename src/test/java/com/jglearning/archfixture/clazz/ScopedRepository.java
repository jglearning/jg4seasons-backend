package com.jglearning.archfixture.clazz;

import com.jglearning.jg4seasons.infra.persistence.AcademyScopedRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;

/** 정상: academyId가 첫 파라미터이고 이름은 By 바로 뒤가 AcademyId, @Query는 :academyId를 쓴다. save는 예외. */
public interface ScopedRepository extends AcademyScopedRepository<ScopedFixtureEntity, Long> {
    List<ScopedFixtureEntity> findByAcademyIdAndName(Long academyId, String name);

    long countByAcademyId(Long academyId);

    List<ScopedFixtureEntity> findByAcademyIdOrderByNameAsc(Long academyId);

    @Query("select e from ScopedFixtureEntity e where e.academyId = :academyId and e.id = :id")
    Optional<ScopedFixtureEntity> findScoped(Long academyId, Long id);

    <S extends ScopedFixtureEntity> S save(S entity);
}
