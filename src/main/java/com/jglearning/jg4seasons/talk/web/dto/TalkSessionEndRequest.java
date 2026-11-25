package com.jglearning.jg4seasons.talk.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * 프리토킹 세션 종료(계약 D-3). 받는 상한(1000줄·한 줄 5000자)은 요청 크기를 막는 선이고, 저장은 서버가 200줄·500자·max_seconds로 자른다
 */
@Schema(name = "TalkSessionEndRequest")
public record TalkSessionEndRequest(
        @Schema(description = "실제 대화 시간(초). 그 세션의 maxSeconds를 넘으면 서버가 잘라 저장", example = "168")
        @NotNull
        @Min(0)
        @Max(86400)
        Integer durationSec,

        @Schema(
                description = "끝난 이유",
                allowableValues = {"user", "timeout", "left", "disconnected"})
        @NotNull
        @Pattern(regexp = "^(user|timeout|left|disconnected)$", message = "없는 종료 이유예요")
        String endReason,

        @Schema(description = "대화 글자 기록(순서대로). 서버는 공백만 있는 줄을 버리고 앞에서부터 200줄, 한 줄 500자까지 저장") @NotNull @Size(max = 1000)
        List<@NotNull @Valid Turn> turns,

        @Schema(
                type = "object",
                nullable = true,
                description = "모델이 이벤트로 알려준 사용량을 합한 값 그대로(모양은 서비스에 따름). JSON 객체만, 16KB까지. 없으면 생략")
        JsonNode usage) {

    @Schema(name = "TalkTurn")
    public record Turn(
            @Schema(allowableValues = {"student", "character"})
            @NotNull
            @Pattern(regexp = "^(student|character)$", message = "없는 역할이에요")
            String role,

            @Schema(example = "I go to school and play soccer.") @NotNull @Size(max = 5000)
            String text) {}
}
