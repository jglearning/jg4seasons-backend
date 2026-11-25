package com.jglearning.jg4seasons.talk.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** 프리토킹 세션 생성(계약 D-2). 지시문은 서버가 만들고 브라우저는 주제·음성 code만 보낸다 */
@Schema(name = "TalkSessionCreateRequest")
public record TalkSessionCreateRequest(
        @Schema(
                description = "클라이언트가 만든 UUID(소문자). 같은 값으로 다시 보내면 새로 만들지 않고 그 세션을 200으로",
                example = "0b9e1c2d-3f4a-4b5c-8d6e-7f8091a2b3c4")
        @NotBlank
        @Pattern(regexp = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", message = "UUID 형식이 아니에요")
        String clientSessionId,

        @Schema(
                description = "주제 카드 code",
                allowableValues = {"lesson", "day", "likes"})
        @NotNull
        @Pattern(regexp = "^(lesson|day|likes)$", message = "없는 주제예요")
        String topic,

        @Schema(
                description = "음성",
                allowableValues = {"female", "male"})
        @NotNull
        @Pattern(regexp = "^(female|male)$", message = "없는 음성이에요")
        String voice) {}
