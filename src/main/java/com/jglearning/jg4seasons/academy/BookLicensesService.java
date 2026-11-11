package com.jglearning.jg4seasons.academy;

import com.jglearning.jg4seasons.academy.api.BookLicenses;
import com.jglearning.jg4seasons.academy.api.LicenseRow;
import com.jglearning.jg4seasons.academy.api.LicenseStatus;
import com.jglearning.jg4seasons.academy.api.LicenseWrite;
import com.jglearning.jg4seasons.infra.web.error.ApiException;
import com.jglearning.jg4seasons.infra.web.error.CommonErrorCode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class BookLicensesService implements BookLicenses {

    /** 학생 수 상한 — 입력 실수(자릿수) 방지 */
    static final int MAX_SEATS = 10_000;

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

    /**
     * 학원 행 잠금으로 같은 학원의 지정을 줄 세우고, 사용권 줄은 잠금 읽기로 읽는다 — 호출자가 앞서 일반 읽기로 스냅숏을 잡았어도 먼저 커밋된 줄을 본다(동시 첫
     * 지정이 둘 다 "없음"을 보고 넣어 유일 키 위반이 나지 않게). 호출자 트랜잭션 안에서만(감사 기록과 함께 롤백)
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public LicenseWrite setLicense(
            long academyId, long bookId, LocalDate startDate, LocalDate endDate, int seats, LocalDate today) {
        if (endDate.isBefore(startDate)) {
            throw ApiException.validation("endDate", "종료일은 시작일과 같거나 뒤여야 해요");
        }
        if (seats < 0 || seats > MAX_SEATS) {
            throw ApiException.validation("seats", "학생 수는 0~" + MAX_SEATS + "명이에요");
        }
        if (academies.lockById(academyId).isEmpty()) {
            throw new ApiException(CommonErrorCode.NOT_FOUND, "학원을 찾을 수 없어요");
        }
        boolean enforced = enforced(academyId);
        Optional<BookLicense> found = licenses.findByAcademyIdAndBookIdForUpdate(academyId, bookId);
        LicenseRow before = found.map(license -> row(enforced, license, today)).orElse(null);
        BookLicense license = found.orElseGet(() -> BookLicense.create(academyId, bookId, startDate, endDate, seats));
        license.change(startDate, endDate, seats);
        BookLicense saved = licenses.saveAndFlush(license);
        return new LicenseWrite(saved.getId(), before, row(enforced, saved, today));
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
