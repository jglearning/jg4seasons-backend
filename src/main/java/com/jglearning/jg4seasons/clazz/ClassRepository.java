package com.jglearning.jg4seasons.clazz;

import com.jglearning.jg4seasons.clazz.api.ClassStatus;
import com.jglearning.jg4seasons.infra.persistence.AcademyScopedRepository;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ClassRepository extends AcademyScopedRepository<Clazz, Long> {

    Optional<Clazz> findByAcademyIdAndId(Long academyId, Long id);

    List<Clazz> findByAcademyIdOrderById(Long academyId);

    List<Clazz> findByAcademyIdAndIdIn(Long academyId, Collection<Long> ids);

    List<Clazz> findByAcademyIdAndStatusOrderById(Long academyId, ClassStatus status);

    /** 같은 반의 동시 변경(이름·상태·코드 재발급·담당 교사)을 줄 세운다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Clazz c where c.academyId = :academyId and c.id = :id")
    Optional<Clazz> findByAcademyIdAndIdForUpdate(@Param("academyId") Long academyId, @Param("id") Long id);

    Clazz save(Clazz clazz);
}
