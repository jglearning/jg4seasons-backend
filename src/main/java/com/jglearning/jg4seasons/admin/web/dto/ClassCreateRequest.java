package com.jglearning.jg4seasons.admin.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/** 5.1 반 생성. 권·개강일·요일은 나중에 바꿀 수 없다(새 반으로). */
public record ClassCreateRequest(
        @Schema(description = "앞뒤 공백을 뺀 1~50자. 같은 학원 안에서 겹쳐도 됨") @NotNull @Size(max = 200)
        String name,

        @Schema(description = "권 코드(예: L2-B1). 없는 권이면 400 VALIDATION(bookCode)", example = "L2-B1")
        @NotNull
        @Size(max = 20)
        String bookCode,

        @Schema(description = "개강일(학원 날짜). 지난 날짜도 됨. 이날이 수업 요일이면 1회차") @NotNull
        LocalDate startDate,

        @Schema(description = "수업 요일 1(월)~7(일), 하나 이상, 겹침 없음. 아니면 400 VALIDATION(weekdays)", example = "[1, 3]")
        @NotNull
        List<@NotNull Integer> weekdays,

        @Schema(description = "담당 교사 id. 그 학원의 활성 교사·관리자만(아니면 404). 빈 배열 허용") @NotNull
        List<@NotNull Long> teacherIds) {}
