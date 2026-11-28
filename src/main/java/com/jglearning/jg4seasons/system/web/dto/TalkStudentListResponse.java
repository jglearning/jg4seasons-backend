package com.jglearning.jg4seasons.system.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** 7.5·7.6 학원 학생별 프리토킹 허용(ADR 0040). 재원생만, 반 이름 → 이름 → id 순, 반 없는 학생은 맨 뒤. 페이지 없음 */
public record TalkStudentListResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<TalkStudentItem> items) {

    public TalkStudentListResponse {
        items = List.copyOf(items);
    }

    @Schema(name = "TalkStudentItem")
    public record TalkStudentItem(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            long id,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String name,

            @JsonProperty("class")
            @Schema(
                    name = "class",
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    nullable = true,
                    description = "지금 반(여러 반이면 id가 가장 작은 반). 반 이동 중이라 반이 없으면 null — 켜 둘 수는 있지만 반이 생겨야 쓸 수 있다")
            @Nullable
            ClassRef clazz,

            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "운영자 허용. 학원 스위치(features.talk)가 꺼져 있으면 true여도 학생은 쓸 수 없다")
            boolean talkEnabled) {}

    @Schema(name = "TalkStudentClassRef")
    public record ClassRef(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            long id,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String name) {}
}
