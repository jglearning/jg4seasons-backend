package com.jglearning.jg4seasons.system;

import com.jglearning.jg4seasons.academy.api.AcademyDirectory;
import com.jglearning.jg4seasons.academy.api.AcademyRegistry;
import com.jglearning.jg4seasons.academy.api.AcademySummary;
import com.jglearning.jg4seasons.academy.api.BookLicenses;
import com.jglearning.jg4seasons.academy.api.LicenseRow;
import com.jglearning.jg4seasons.academy.api.LicenseWrite;
import com.jglearning.jg4seasons.auth.api.Operators;
import com.jglearning.jg4seasons.clazz.api.LicenseUsage;
import com.jglearning.jg4seasons.clazz.api.LicenseUsages;
import com.jglearning.jg4seasons.content.api.BookDirectory;
import com.jglearning.jg4seasons.content.api.BookLessons;
import com.jglearning.jg4seasons.content.api.BookSummary;
import com.jglearning.jg4seasons.infra.audit.AuditAction;
import com.jglearning.jg4seasons.infra.audit.AuditLogger;
import com.jglearning.jg4seasons.infra.audit.AuditTarget;
import com.jglearning.jg4seasons.infra.web.error.ApiException;
import com.jglearning.jg4seasons.infra.web.error.CommonErrorCode;
import com.jglearning.jg4seasons.system.web.dto.AcademyDetailResponse;
import com.jglearning.jg4seasons.system.web.dto.LicenseItem;
import com.jglearning.jg4seasons.system.web.dto.LicenseSetRequest;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 7.3·7.4 운영자 학원 상세·교재 사용권 지정(SYS-07, ADR 0031). 사용권은 academy, 사용 학생 수는 clazz, 권 표시는 content에서 받아 조립한다. 오늘 = 그
 * 학원 타임존 날짜. 모든 메서드는 먼저 요청한 사람이 지금도 활성 운영자인지 확인한다.
 */
@Service
@Transactional
public class SystemLicenseService {

    private final Operators operators;
    private final AcademyRegistry registry;
    private final AcademyDirectory academies;
    private final BookLicenses licenses;
    private final LicenseUsages usages;
    private final BookDirectory books;
    private final AuditLogger auditLogger;
    private final Clock clock;

    SystemLicenseService(
            Operators operators,
            AcademyRegistry registry,
            AcademyDirectory academies,
            BookLicenses licenses,
            LicenseUsages usages,
            BookDirectory books,
            AuditLogger auditLogger,
            Clock clock) {
        this.operators = operators;
        this.registry = registry;
        this.academies = academies;
        this.licenses = licenses;
        this.usages = usages;
        this.books = books;
        this.auditLogger = auditLogger;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AcademyDetailResponse detail(long actorId, long academyId) {
        operators.requireActiveOperator(actorId);
        AcademySummary academy = registry.find(academyId);
        LocalDate today = today(academyId);
        return new AcademyDetailResponse(
                SystemAcademyService.toResponse(academy),
                today,
                usages.of(academyId, today).stream().map(this::toItem).toList());
    }

    /**
     * 학원 관리 쓰기는 학원 행 잠금으로 줄 세운다(다른 관리 쓰기와 같은 규칙, 가장 먼저). 사용권 줄 자체의 동시 지정은 BookLicenses.setLicense가 스스로
     * 지킨다(학원 잠금 + 잠금 읽기). 학원이 없으면 잠금은 아무것도 하지 않고 아래에서 404
     */
    public LicenseItem setLicense(long actorId, long academyId, String bookCode, LicenseSetRequest request) {
        academies.lockForAdminWrite(academyId);
        operators.requireActiveOperator(actorId);
        registry.find(academyId);
        long bookId = books.findLessons(bookCode)
                .map(BookLessons::bookId)
                .orElseThrow(() -> new ApiException(CommonErrorCode.NOT_FOUND, "교재를 찾을 수 없어요"));
        LocalDate today = today(academyId);
        LicenseWrite write =
                licenses.setLicense(academyId, bookId, request.startDate(), request.endDate(), request.seats(), today);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("bookCode", books.summaryOf(bookId).bookCode());
        detail.put("before", period(write.before()));
        detail.put("after", period(write.after()));
        auditLogger.record(
                academyId, actorId, AuditAction.LICENSE_SET, AuditTarget.BOOK_LICENSE, write.licenseId(), detail);
        return usages.of(academyId, today).stream()
                .filter(usage -> usage.row().bookId() == bookId)
                .findFirst()
                .map(this::toItem)
                .orElseThrow(() -> new IllegalStateException("방금 지정한 사용권이 없다: academy " + academyId));
    }

    private LocalDate today(long academyId) {
        return LocalDate.now(clock.withZone(academies.zoneOf(academyId)));
    }

    private static @Nullable Map<String, Object> period(@Nullable LicenseRow row) {
        if (row == null) {
            return null;
        }
        return Map.of(
                "startDate", row.startDate().toString(),
                "endDate", row.endDate().toString(),
                "seats", row.seats());
    }

    private LicenseItem toItem(LicenseUsage usage) {
        LicenseRow row = usage.row();
        BookSummary book = books.summaryOf(row.bookId());
        return new LicenseItem(
                new LicenseItem.BookRef(book.bookCode(), book.label(), book.season()),
                row.startDate(),
                row.endDate(),
                row.seats(),
                usage.usedSeats(),
                row.status().value(),
                row.daysLeft());
    }
}
