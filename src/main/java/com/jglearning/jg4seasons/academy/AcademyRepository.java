package com.jglearning.jg4seasons.academy;

import jakarta.persistence.LockModeType;
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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a.id from Academy a where a.id = :id")
    Optional<Long> lockById(@Param("id") Long id);
}
