package com.jglearning.jg4seasons.teacher.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/** 4.5 진도표(T-06) 전체. 휴강 행도 온다(매트릭스와 다름). 4.6·4.7도 바뀐 뒤 전체를 돌려준다. */
public record SessionListResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "seq 순")
        List<SessionItem> items) {

    public record SessionItem(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "진도표 행 번호(휴강 행도 하나 차지 — 24를 넘을 수 있음)")
            int seq,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            TeacherLessonRef lesson,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            LocalDate scheduledDate,

            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    allowableValues = {"scheduled", "done", "cancelled"})
            String status,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
            String note) {}
}
