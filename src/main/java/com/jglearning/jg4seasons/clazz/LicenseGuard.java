package com.jglearning.jg4seasons.clazz;

import com.jglearning.jg4seasons.academy.api.BookLicenses;
import com.jglearning.jg4seasons.clazz.api.ClassStatus;
import com.jglearning.jg4seasons.infra.web.error.ApiException;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.OptionalInt;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 반 쓰기의 사용권 확인(ADR 0031): 학생을 반에 들이는 쓰기(등록·이동·끝난 반 되살리기)가 함께 쓴다. 판정 자체는 academy(BookLicenses). 학원 잠금
 * (lockForAdminWrite) 안에서만 부른다 — 그래야 동시 쓰기에도 학생 수 한도가 지켜진다.
 */
@Component
class LicenseGuard {

    private final BookLicenses licenses;
    private final EnrollmentRepository enrollments;

    LicenseGuard(BookLicenses licenses, EnrollmentRepository enrollments) {
        this.licenses = licenses;
        this.enrollments = enrollments;
    }

    /** 개강 준비 쓰기: 그 권이 시작 전·기간 안이 아니면 403. today = 학원 타임존 날짜 */
    void requirePreparation(long academyId, long bookId, LocalDate today) {
        if (!licenses.statusOf(academyId, bookId, today).allowsPreparation()) {
            throw new ApiException(ClassErrorCode.LICENSE_INACTIVE, "사용 기간이 아니에요");
        }
    }

    /**
     * 학생들이 그 권 active 반에 들어올 때: (지금 학생 ∪ 들어올 학생)이 지금보다 늘고 한도를 넘으면 409. 이미 세어진 학생(같은 권 반끼리 이동)은 자리를 더 쓰지
     * 않으므로, 한도를 낮춰 이미 넘은 상태여도 막지 않는다
     */
    void requireSeats(long academyId, long bookId, Collection<Long> joining) {
        OptionalInt limit = licenses.seatLimit(academyId, bookId);
        if (limit.isEmpty()) {
            return;
        }
        Set<Long> seated =
                new HashSet<>(enrollments.findCurrentStudentIdsOfBook(academyId, bookId, ClassStatus.ACTIVE));
        int before = seated.size();
        seated.addAll(joining);
        if (seated.size() > before && seated.size() > limit.getAsInt()) {
            throw new ApiException(ClassErrorCode.LICENSE_SEATS_FULL, "사용할 수 있는 학생 수를 넘었어요");
        }
    }
}
