package com.jglearning.jg4seasons.clazz;

import com.jglearning.jg4seasons.academy.api.BookLicenses;
import com.jglearning.jg4seasons.clazz.api.ClassStatus;
import com.jglearning.jg4seasons.clazz.api.LicenseUsage;
import com.jglearning.jg4seasons.clazz.api.LicenseUsages;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 사용 학생 수는 등록·이동 한도(LicenseGuard.requireSeats)와 같은 조회로 센다 — 화면 숫자와 409가 어긋나지 않게. */
@Service
@Transactional(readOnly = true)
class LicenseUsagesService implements LicenseUsages {

    private final BookLicenses licenses;
    private final EnrollmentRepository enrollments;

    LicenseUsagesService(BookLicenses licenses, EnrollmentRepository enrollments) {
        this.licenses = licenses;
        this.enrollments = enrollments;
    }

    @Override
    public List<LicenseUsage> of(long academyId, LocalDate today) {
        return licenses.licensesOf(academyId, today).stream()
                .map(row -> new LicenseUsage(
                        row,
                        enrollments
                                .findCurrentStudentIdsOfBook(academyId, row.bookId(), ClassStatus.ACTIVE)
                                .size()))
                .toList();
    }
}
