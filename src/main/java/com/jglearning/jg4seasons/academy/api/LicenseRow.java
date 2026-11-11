package com.jglearning.jg4seasons.academy.api;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * 학원·권 사용권 한 줄(7.3). 날짜는 학원 타임존 날짜, 시작일·종료일 둘 다 포함. status = statusOf와 같은 판정(사용권을 적용하지 않는 학원은 늘 ACTIVE).
 * daysLeft = 기간 안이면 종료일 − 오늘 + 1(마지막 날 1), 그 밖·적용 안 함은 null.
 */
public record LicenseRow(
        long bookId,
        LocalDate startDate,
        LocalDate endDate,
        int seats,
        LicenseStatus status,
        @Nullable Integer daysLeft) {}
