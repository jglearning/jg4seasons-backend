package com.jglearning.jg4seasons.academy;

import com.jglearning.jg4seasons.academy.api.BookLicenses;
import com.jglearning.jg4seasons.academy.api.LicenseRow;
import com.jglearning.jg4seasons.academy.api.LicenseStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class BookLicensesService implements BookLicenses {

    private final AcademyRepository academies;
    private final BookLicenseRepository licenses;

    BookLicensesService(AcademyRepository academies, BookLicenseRepository licenses) {
        this.academies = academies;
        this.licenses = licenses;
    }

    @Override
    public LicenseStatus statusOf(long academyId, long bookId, LocalDate today) {
        if (!enforced(academyId)) {
            return LicenseRule.status(false, null, null, today);
        }
        Optional<BookLicense> license = licenses.findByAcademyIdAndBookId(academyId, bookId);
        return LicenseRule.status(
                true,
                license.map(BookLicense::getStartDate).orElse(null),
                license.map(BookLicense::getEndDate).orElse(null),
                today);
    }

    @Override
    public OptionalInt seatLimit(long academyId, long bookId) {
        if (!enforced(academyId)) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(licenses.findByAcademyIdAndBookId(academyId, bookId)
                .map(BookLicense::getSeats)
                .orElse(0));
    }

    @Override
    public List<LicenseRow> licensesOf(long academyId, LocalDate today) {
        boolean enforced = enforced(academyId);
        return licenses.findByAcademyIdOrderByBookIdAsc(academyId).stream()
                .map(license -> row(enforced, license, today))
                .toList();
    }

    private static LicenseRow row(boolean enforced, BookLicense license, LocalDate today) {
        LicenseStatus status = LicenseRule.status(enforced, license.getStartDate(), license.getEndDate(), today);
        return new LicenseRow(
                license.getBookId(),
                license.getStartDate(),
                license.getEndDate(),
                license.getSeats(),
                status,
                LicenseRule.daysLeft(status, enforced ? license.getEndDate() : null, today));
    }

    /** 학원은 사용자·반의 FK라 반드시 있다 — 없으면 데이터 오류 */
    private boolean enforced(long academyId) {
        return academies
                .findById(academyId)
                .map(Academy::isLicenseEnforced)
                .orElseThrow(() -> new IllegalStateException("학원이 없다: " + academyId));
    }
}
