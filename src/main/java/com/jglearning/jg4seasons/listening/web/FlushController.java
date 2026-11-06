package com.jglearning.jg4seasons.listening.web;

import com.jglearning.jg4seasons.infra.security.CsrfTokens;
import com.jglearning.jg4seasons.infra.security.Principals;
import com.jglearning.jg4seasons.infra.web.error.ApiException;
import com.jglearning.jg4seasons.infra.web.error.CommonErrorCode;
import com.jglearning.jg4seasons.listening.FlushService;
import com.jglearning.jg4seasons.listening.FlushService.FlushCommand;
import com.jglearning.jg4seasons.listening.segment.SegmentFilter.IncomingSegment;
import com.jglearning.jg4seasons.listening.web.dto.FlushRequest;
import com.jglearning.jg4seasons.listening.web.dto.FlushResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.security.RolesAllowed;
import jakarta.servlet.http.HttpServletRequest;
import java.util.regex.Pattern;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * 3.1 청취 구간 전송(S-05 플레이어). 학생만. fetch는 application/json + 헤더 X-CSRF, sendBeacon은 text/plain + 바디 csrf. 이 경로만 Security CSRF
 * 필터에서 빠져 있어 여기서 확인한다(CsrfTokens) — 이 확인을 지우면 CSRF가 열린다. 바디는 문자열로 받아 엄격 JSON(StrictJsonConfig 설정 JsonMapper)으로
 * 읽는다(text/plain도 같은 규칙).
 */
@RestController
@RolesAllowed("STUDENT")
public class FlushController {

    private static final Pattern UUID =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final FlushService service;
    private final JsonMapper jsonMapper;

    FlushController(FlushService service, JsonMapper jsonMapper) {
        this.service = service;
        this.jsonMapper = jsonMapper;
    }

    @Operation(
            summary = "청취 구간 전송",
            description = "같은 세션의 같은 구간은 다시 세지 않는다(멱등). 잘못된 세그먼트는 버리고 dropped로 알린다(200)."
                    + " 내 반 진도표에 없는 회차 트랙 404, 수업일 전 423 LESSON_LOCKED, 다른 학생·트랙의 세션 id 400."
                    + " CSRF는 헤더 X-CSRF 또는 바디 csrf(sendBeacon)")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = {
                @Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = FlushRequest.class)),
                @Content(mediaType = MediaType.TEXT_PLAIN_VALUE, schema = @Schema(implementation = FlushRequest.class))
            })
    @PostMapping(
            value = "/api/v1/listening/flush",
            consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_PLAIN_VALUE})
    public FlushResponse flush(
            @AuthenticationPrincipal Jwt jwt, @RequestBody String body, HttpServletRequest httpRequest) {
        FlushRequest request = read(body, httpRequest);
        if (!CsrfTokens.matches(httpRequest, request.csrf())) {
            throw new ApiException(CommonErrorCode.FORBIDDEN);
        }
        return service.flush(Principals.academyId(jwt), Principals.userId(jwt), command(request));
    }

    private FlushRequest read(String body, HttpServletRequest httpRequest) {
        try {
            return jsonMapper.readValue(body, FlushRequest.class);
        } catch (JacksonException e) {
            throw new HttpMessageNotReadableException(
                    "flush 요청 JSON을 읽을 수 없다", e, new ServletServerHttpRequest(httpRequest));
        }
    }

    /** 필수 칸 확인. 세그먼트 안의 빠진 칸은 400이 아니라 그 세그먼트만 버린다(SegmentFilter) */
    private static FlushCommand command(FlushRequest request) {
        if (request.clientSessionId() == null
                || !UUID.matcher(request.clientSessionId()).matches()) {
            throw ApiException.validation("clientSessionId", "세션 id는 UUID 형식이에요");
        }
        if (request.trackId() == null || request.trackId() <= 0) {
            throw ApiException.validation("trackId", "트랙 id가 필요해요");
        }
        if (request.startedAt() == null) {
            throw ApiException.validation("startedAt", "시작 시각이 필요해요");
        }
        if (request.segments() == null) {
            throw ApiException.validation("segments", "구간 목록이 필요해요(빈 배열 가능)");
        }
        return new FlushCommand(
                request.clientSessionId().toLowerCase(java.util.Locale.ROOT),
                request.trackId(),
                request.startedAt(),
                request.userAgent(),
                request.segments().stream()
                        .map(segment -> segment == null
                                ? null
                                : new IncomingSegment(segment.startMs(), segment.endMs(), segment.recordedAt()))
                        .toList());
    }
}
