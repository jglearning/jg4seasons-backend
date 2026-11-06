package com.jglearning.jg4seasons.listening.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * 3.1 청취 구간 전송. application/json(fetch, CSRF는 헤더 X-CSRF)과 text/plain(sendBeacon, CSRF는 바디 csrf) 둘 다 이 모양. 필수 칸은 서버가
 * 직접 확인한다(바디를 문자열로 받아 읽기 때문). 세그먼트의 빠진 칸·범위 위반은 400이 아니라 그 세그먼트만 버린다(dropped).
 */
@Schema(name = "FlushRequest")
public record FlushRequest(
        @Schema(description = "sendBeacon(text/plain)일 때만. fetch는 헤더 X-CSRF")
        String csrf,

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "트랙 재생 시작 때 클라이언트가 만든 UUID. 재전송·멱등 키",
                example = "7c2e0000-0000-4000-8000-000000000001")
        String clientSessionId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long trackId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "세션 시작 시각(클라이언트, 첫 flush만 저장)")
        Instant startedAt,

        @Schema(description = "첫 flush만 저장, 200자에서 자름") String userAgent,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "빈 배열 허용. 앞 50개만 보고 나머지는 dropped")
        List<Segment> segments,

        @JsonProperty("final") @Schema(name = "final", description = "pause·ended·pagehide에서 true. 서버 동작은 같다")
        Boolean isFinal) {

    @Schema(name = "FlushSegment")
    public record Segment(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "트랙 안 시작(ms)")
            Integer startMs,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "트랙 안 끝(ms), 시작보다 큼")
            Integer endMs,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "기록 시각(클라이언트)")
            Instant recordedAt) {}
}
