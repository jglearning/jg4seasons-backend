package com.jglearning.jg4seasons.academy;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

interface AcademyRepository extends Repository<Academy, Long> {

    Optional<Academy> findById(Long id);

    Optional<Academy> findBySlug(String slug);

    /** 첫 학원 부트스트랩: 학원이 하나도 없는지 */
    long count();

    Academy save(Academy academy);

    /** 운영자 학원 만들기: slug 유일 키 위반을 이 자리에서 드러내려고 바로 flush */
    Academy saveAndFlush(Academy academy);

    /** 운영자 학원 목록 */
    List<Academy> findAllByOrderByIdAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a.id from Academy a where a.id = :id")
    Optional<Long> lockById(@Param("id") Long id);
}
