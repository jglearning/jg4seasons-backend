package com.jglearning.jg4seasons.system.web.dto;

import com.jglearning.jg4seasons.academy.api.BookLicenses;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** 7.4 사용권 지정. 날짜는 학원 타임존 날짜, 둘 다 포함. */
public record LicenseSetRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull
        LocalDate startDate,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "시작일과 같거나 뒤") @NotNull
        LocalDate endDate,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "학생 수 한도(산 교재 부수) 0~10000")
        @NotNull
        @Min(value = 0, message = BookLicenses.SEATS_MESSAGE)
        @Max(value = BookLicenses.MAX_SEATS, message = BookLicenses.SEATS_MESSAGE)
        Integer seats) {}
