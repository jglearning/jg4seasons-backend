package com.jglearning.jg4seasons.academy;

import com.jglearning.jg4seasons.infra.persistence.AcademyScopedRepository;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface BookLicenseRepository extends AcademyScopedRepository<BookLicense, Long> {

    Optional<BookLicense> findByAcademyIdAndBookId(long academyId, long bookId);

    List<BookLicense> findByAcademyIdOrderByBookIdAsc(long academyId);

    /** 사용권 지정: 잠금 읽기(최신 커밋을 본다 — 스냅숏 무관). 학원 행 잠금 뒤에 부른다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from BookLicense l where l.academyId = :academyId and l.bookId = :bookId")
    Optional<BookLicense> findByAcademyIdAndBookIdForUpdate(
            @Param("academyId") long academyId, @Param("bookId") long bookId);

    BookLicense saveAndFlush(BookLicense license);
}
