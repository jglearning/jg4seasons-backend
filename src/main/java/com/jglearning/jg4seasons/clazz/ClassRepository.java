package com.jglearning.jg4seasons.clazz;

import com.jglearning.jg4seasons.clazz.api.ClassStatus;
import com.jglearning.jg4seasons.infra.persistence.AcademyScopedRepository;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ClassRepository extends AcademyScopedRepository<Clazz, Long> {

    Optional<Clazz> findByAcademyIdAndId(Long academyId, Long id);

    List<Clazz> findByAcademyIdOrderById(Long academyId);

    List<Clazz> findByAcademyIdAndStatusOrderById(Long academyId, ClassStatus status);

    /** 같은 반의 동시 변경(이름·상태·코드 재발급·담당 교사)을 줄 세운다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Clazz c where c.academyId = :academyId and c.id = :id")
    Optional<Clazz> findByAcademyIdAndIdForUpdate(@Param("academyId") Long academyId, @Param("id") Long id);

    /**
     * 그 학원 반 행을 모두 잠근다(교사 기준 담당 반 교체용). 담당 교사 배정을 바꾸는 두 경로(반 기준·교사 기준)가 모두 "반 행 → class_teacher" 순서로
     * 잠가 교착을 피한다. 여러 행을 잠그는 곳은 이 쿼리 하나라 같은 인덱스 순서로만 잠긴다
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c.id from Clazz c where c.academyId = :academyId order by c.id")
    List<Long> lockAllByAcademyId(@Param("academyId") Long academyId);

    Clazz save(Clazz clazz);
}
