package com.jglearning.jg4seasons.academy;

import com.jglearning.jg4seasons.infra.persistence.AcademyScopedRepository;
import java.util.List;
import java.util.Optional;

interface BookLicenseRepository extends AcademyScopedRepository<BookLicense, Long> {

    Optional<BookLicense> findByAcademyIdAndBookId(long academyId, long bookId);

    List<BookLicense> findByAcademyIdOrderByBookIdAsc(long academyId);

    BookLicense saveAndFlush(BookLicense license);
}
