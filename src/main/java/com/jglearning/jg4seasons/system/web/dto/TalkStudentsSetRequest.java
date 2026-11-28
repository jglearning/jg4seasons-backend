package com.jglearning.jg4seasons.system.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 7.6 학생별 프리토킹 켜기·끄기(ADR 0040). 한 명·여러 명 한 번에, 같은 값이면 그대로 */
public record TalkStudentsSetRequest(
        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "1~500명. 같은 id는 한 번으로. 그 학원 재원생이 아닌 id가 하나라도 있으면 404이고 아무것도 바뀌지 않는다")
        @NotNull
        @Size(min = 1, max = MAX_STUDENTS, message = "학생은 1~500명까지 고를 수 있어요")
        List<@NotNull Long> studentIds,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull
        Boolean enabled) {

    public static final int MAX_STUDENTS = 500;
}
