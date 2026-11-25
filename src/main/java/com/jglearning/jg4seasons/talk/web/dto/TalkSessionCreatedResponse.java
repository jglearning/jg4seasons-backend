package com.jglearning.jg4seasons.talk.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** 프리토킹 세션 생성 결과(계약 D-2 + 추가 지시 가-2 closingInstruction) */
@Schema(name = "TalkSessionCreatedResponse")
public record TalkSessionCreatedResponse(
        @Schema(description = "세션 id(종료·조회에 쓴다)", requiredMode = Schema.RequiredMode.REQUIRED)
        long id,

        @Schema(
                description = "세션 상태. 새 세션은 created. 이미 끝난 세션의 clientSessionId를 다시 보내면 그 상태 그대로",
                allowableValues = {"created", "ended", "expired", "failed"},
                requiredMode = Schema.RequiredMode.REQUIRED)
        String status,

        @Schema(description = "이 세션의 시간 제한(초)", example = "180", requiredMode = Schema.RequiredMode.REQUIRED)
        int maxSeconds,

        @Schema(
                description = "모델 연결 정보. status가 created일 때만 있고 그 밖에는 null(새 clientSessionId로 다시 시작)",
                requiredMode = Schema.RequiredMode.REQUIRED,
                nullable = true)
        Realtime realtime) {

    @Schema(name = "TalkRealtime")
    public record Realtime(
            @Schema(
                    description = "임시 키(ek_…). 브라우저가 SDP 교환에 Bearer로 쓴다. 저장·로그 금지",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            String clientSecret,

            @Schema(
                    description = "임시 키 만료 시각(UTC). 이 전에 연결을 시작해야 한다. 시작한 대화는 만료 뒤에도 이어진다",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            Instant expiresAt,

            @Schema(
                    description = "연결할 모델 이름",
                    example = "gpt-realtime-2.1-mini",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            String model,

            @Schema(
                    description = "시간이 다 됐을 때 연결된 세션에 시스템 메시지로 넣을 문장(문구를 바꾸지 않고 그대로). 넣은 뒤 응답을 시작시키면 캐릭터가 인사하고 끝낸다",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            String closingInstruction) {}
}
