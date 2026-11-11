package com.jglearning.jg4seasons.academy;

import com.jglearning.jg4seasons.academy.api.LicenseStatus;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.jspecify.annotations.Nullable;

/** 사용권 판정 한 곳(ADR 0031 결정 3·4). 날짜는 모두 학원 타임존 날짜, 시작일·종료일 둘 다 포함. */
final class LicenseRule {

    private LicenseRule() {}

    /**
     * 남은 일수: 사용권을 적용하는 학원이고 기간 안(ACTIVE)이며 종료일이 있으면 종료일 − 오늘 + 1(마지막 날 1), 그 밖(적용 안 함·시작 전·만료·줄 없음)은
     * null. 학원 날짜의 "오늘"은 서버만 알아 응답에 싣는다(7.3)
     */
    static @Nullable Integer daysLeft(
            boolean enforced, LicenseStatus status, @Nullable LocalDate end, LocalDate today) {
        if (!enforced || status != LicenseStatus.ACTIVE || end == null) {
            return null;
        }
        return Math.toIntExact(ChronoUnit.DAYS.between(today, end) + 1);
    }

    /**
     * enforced = 학원이 사용권을 적용하는가. start·end는 그 학원·권의 사용권 기간(없으면 둘 다 null). 적용 안 함 → ACTIVE, 사용권 없음 → EXPIRED
     */
    static LicenseStatus status(boolean enforced, @Nullable LocalDate start, @Nullable LocalDate end, LocalDate today) {
        if (!enforced) {
            return LicenseStatus.ACTIVE;
        }
        if (start == null || end == null) {
            return LicenseStatus.EXPIRED;
        }
        if (today.isBefore(start)) {
            return LicenseStatus.BEFORE_START;
        }
        return today.isAfter(end) ? LicenseStatus.EXPIRED : LicenseStatus.ACTIVE;
    }
}
