package com.jglearning.jg4seasons.talk.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 프리토킹 세션 종료(계약 D-3)·조회(D-4) 결과 */
@Schema(name = "TalkSessionResultResponse")
public record TalkSessionResultResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long id,

        @Schema(
                allowableValues = {"created", "ended", "expired", "failed"},
                requiredMode = Schema.RequiredMode.REQUIRED)
        String status,

        @Schema(
                description = "저장된 대화 시간(초, maxSeconds로 자른 값). 종료 전이면 null",
                requiredMode = Schema.RequiredMode.REQUIRED,
                nullable = true)
        Integer durationSec,

        @Schema(
                description = "학생이 말한 줄 수(자른 뒤). 종료 전이면 null",
                requiredMode = Schema.RequiredMode.REQUIRED,
                nullable = true)
        Integer studentTurnCount,

        @Schema(
                description = "대화 요약. 종료 전·요약 실패·요약 중(종료 직후 조회)·학생이 말한 줄이 없으면 null",
                requiredMode = Schema.RequiredMode.REQUIRED,
                nullable = true)
        Summary summary) {

    @Schema(name = "TalkSummary")
    public record Summary(
            @Schema(description = "잘 쓴 표현 0~2개(학생이 실제로 쓴 표현만)", requiredMode = Schema.RequiredMode.REQUIRED)
            List<String> goodExpressions,

            @Schema(
                    description = "고쳐볼 문장 0~3개. said는 학생이 실제로 말한 문장(학생 줄이 2개 미만이면 빈 배열)",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            List<Correction> corrections) {}

    @Schema(name = "TalkCorrection")
    public record Correction(
            @Schema(description = "학생이 말한 문장 그대로", requiredMode = Schema.RequiredMode.REQUIRED)
            String said,

            @Schema(description = "더 자연스러운 문장", requiredMode = Schema.RequiredMode.REQUIRED)
            String better,

            @Schema(description = "한국어 해요체 한 줄 설명", requiredMode = Schema.RequiredMode.REQUIRED)
            String note) {}
}
