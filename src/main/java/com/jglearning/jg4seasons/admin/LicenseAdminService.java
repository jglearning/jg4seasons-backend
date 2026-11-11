package com.jglearning.jg4seasons.admin;

import com.jglearning.jg4seasons.academy.api.AcademyDirectory;
import com.jglearning.jg4seasons.academy.api.AcademyRegistry;
import com.jglearning.jg4seasons.academy.api.LicenseRow;
import com.jglearning.jg4seasons.admin.web.dto.LicenseListResponse;
import com.jglearning.jg4seasons.auth.api.StaffAccounts;
import com.jglearning.jg4seasons.clazz.api.LicenseUsage;
import com.jglearning.jg4seasons.clazz.api.LicenseUsages;
import com.jglearning.jg4seasons.content.api.BookDirectory;
import com.jglearning.jg4seasons.content.api.BookSummary;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 7.5 학원관리자 교재 사용권 보기(A-05, SYS-07). 사용권·사용 학생 수는 clazz(운영자 7.3과 같은 조회), 권 표시는 content. 학원은 컨트롤러가 JWT에서 넘긴
 * 값뿐이고, 먼저 요청한 사람이 지금도 그 학원의 활성 관리자인지 확인한다(남은 access 대비).
 */
@Service
@Transactional(readOnly = true)
public class LicenseAdminService {

    private final StaffAccounts staff;
    private final AcademyRegistry registry;
    private final AcademyDirectory academies;
    private final LicenseUsages usages;
    private final BookDirectory books;
    private final Clock clock;

    LicenseAdminService(
            StaffAccounts staff,
            AcademyRegistry registry,
            AcademyDirectory academies,
            LicenseUsages usages,
            BookDirectory books,
            Clock clock) {
        this.staff = staff;
        this.registry = registry;
        this.academies = academies;
        this.usages = usages;
        this.books = books;
        this.clock = clock;
    }

    public LicenseListResponse licenses(long academyId, long actorId) {
        staff.requireActiveAdmin(academyId, actorId);
        LocalDate today = LocalDate.now(clock.withZone(academies.zoneOf(academyId)));
        return new LicenseListResponse(
                registry.find(academyId).licenseEnforced(),
                today,
                usages.of(academyId, today).stream().map(this::toItem).toList());
    }

    private LicenseListResponse.Item toItem(LicenseUsage usage) {
        LicenseRow row = usage.row();
        BookSummary book = books.summaryOf(row.bookId());
        return new LicenseListResponse.Item(
                new LicenseListResponse.BookRef(book.bookCode(), book.label(), book.season()),
                row.startDate(),
                row.endDate(),
                row.seats(),
                usage.usedSeats(),
                row.status().value(),
                row.daysLeft());
    }
}
