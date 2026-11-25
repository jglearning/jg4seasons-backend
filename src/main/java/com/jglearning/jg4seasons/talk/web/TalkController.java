package com.jglearning.jg4seasons.talk.web;

import com.jglearning.jg4seasons.infra.security.Principals;
import com.jglearning.jg4seasons.infra.web.error.ApiException;
import com.jglearning.jg4seasons.talk.TalkConfigService;
import com.jglearning.jg4seasons.talk.TalkEndReason;
import com.jglearning.jg4seasons.talk.TalkEndService;
import com.jglearning.jg4seasons.talk.TalkRole;
import com.jglearning.jg4seasons.talk.TalkSessionService;
import com.jglearning.jg4seasons.talk.TalkTopic;
import com.jglearning.jg4seasons.talk.TalkTranscript;
import com.jglearning.jg4seasons.talk.summary.TalkSummary;
import com.jglearning.jg4seasons.talk.web.dto.TalkConfigResponse;
import com.jglearning.jg4seasons.talk.web.dto.TalkSessionCreateRequest;
import com.jglearning.jg4seasons.talk.web.dto.TalkSessionCreatedResponse;
import com.jglearning.jg4seasons.talk.web.dto.TalkSessionEndRequest;
import com.jglearning.jg4seasons.talk.web.dto.TalkSessionResultResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 캐릭터 프리토킹 학생 API(1단계, 계약 D). 학생만. 학원·학생은 access 쿠키(JWT)에서만. 그 학원에서 기능이 꺼져 있으면 모두 404 FEATURE_DISABLED. 쓰기는 X-CSRF
 * 필요. 음성은 브라우저와 모델이 직접 주고받고 서버는 지시문을 묶은 임시 키만 준다
 */
@RestController
@RolesAllowed("STUDENT")
public class TalkController {

    /** usage 상한(직렬화 바이트). 모델 사용량 이벤트 합은 수백 바이트 — 넘으면 잘못 보낸 것 */
    static final int MAX_USAGE_BYTES = 16 * 1024;

    private final TalkConfigService configs;
    private final TalkSessionService sessions;
    private final TalkEndService ends;
    private final JsonMapper json;

    TalkController(TalkConfigService configs, TalkSessionService sessions, TalkEndService ends, JsonMapper json) {
        this.configs = configs;
        this.sessions = sessions;
        this.ends = ends;
        this.json = json;
    }

    @Operation(
            summary = "프리토킹 설정",
            description = "시작 화면 재료: 구독 여부, 레벨, 시간 제한, 오늘 남은 횟수, 주제 카드, 오늘 회차 단어."
                    + " 구독이 없으면 200에 entitled false(lessonWords는 빈 배열). 기능 꺼짐 404 FEATURE_DISABLED(진입 버튼을 숨길 신호),"
                    + " 반이 없으면 404 NOT_FOUND")
    @GetMapping("/api/v1/talk/config")
    public TalkConfigResponse talkConfig(@AuthenticationPrincipal Jwt jwt) {
        TalkConfigService.Config config = configs.config(Principals.academyId(jwt), Principals.userId(jwt));
        return new TalkConfigResponse(
                config.entitled(),
                config.level().slug(),
                config.maxSeconds(),
                config.remainingToday(),
                config.topics().stream()
                        .map(topic -> new TalkConfigResponse.Topic(topic.code(), topic.title()))
                        .toList(),
                config.lessonWords().stream()
                        .map(word -> new TalkConfigResponse.LessonWordItem(word.english(), word.korean()))
                        .toList());
    }

    @Operation(
            summary = "프리토킹 세션 시작",
            description = "세션을 기록하고 서버가 조립한 지시문(레벨·오늘 회차 단어·주제·안전 규칙)과 목소리(반의 레벨 캐릭터 성별 — 학생이 고르지 않음)를 묶은 임시 키를 준다."
                    + " 같은 clientSessionId를 다시 보내면 새로 만들지 않는다: 그 세션이 created이고 만든 지 60초(임시 키 수명) 안이면"
                    + " 임시 키만 새로 200, 그 밖(끝난 세션·60초 지남)은 200에 realtime null(새 clientSessionId로 다시 시작),"
                    + " 다른 학생이 쓴 값이면 400 VALIDATION."
                    + " 기능 꺼짐 404 FEATURE_DISABLED, 구독 없음 403 SUBSCRIPTION_REQUIRED, 반이 없으면 404 NOT_FOUND,"
                    + " 사용 기간 아님 403 LICENSE_INACTIVE, 하루(학원 날짜) 3회 넘으면 429 DAILY_LIMIT(발급에 실패한 세션·연결 전에 떠난 0초 세션은 세지 않음),"
                    + " 모델 서비스에서 임시 키를 받지 못하면 503 REALTIME_UNAVAILABLE")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "새 세션(status created)"),
        @ApiResponse(responseCode = "200", description = "같은 clientSessionId의 기존 세션")
    })
    @PostMapping("/api/v1/talk/sessions")
    public ResponseEntity<TalkSessionCreatedResponse> createTalkSession(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody TalkSessionCreateRequest request) {
        TalkSessionService.Result result = sessions.create(
                Principals.academyId(jwt),
                Principals.userId(jwt),
                request.clientSessionId(),
                TalkTopic.ofCode(request.topic()).orElseThrow(() -> new IllegalStateException("topic 검증과 enum이 어긋났다")));
        TalkSessionService.Result.Connection connection = result.connection();
        TalkSessionCreatedResponse.Realtime realtime = connection == null
                ? null
                : new TalkSessionCreatedResponse.Realtime(
                        connection.secret().clientSecret(),
                        connection.secret().expiresAt(),
                        connection.secret().model(),
                        connection.closingInstruction());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(new TalkSessionCreatedResponse(
                        result.id(), result.status().code(), result.maxSeconds(), realtime));
    }

    @Operation(
            summary = "프리토킹 세션 종료",
            description = "글자 기록·대화 시간·종료 이유를 저장하고 요약(잘 쓴 표현·고쳐볼 문장)을 만들어 돌려준다(최대 20초)."
                    + " 저장은 공백 줄을 버리고 앞에서부터 200줄·한 줄 500자, durationSec는 maxSeconds까지 자른다."
                    + " 요약을 만들지 못하면 summary null(세션은 ended). 종료할 수 있는 건 created뿐 — 이미 ended면 저장된 결과를 그대로 200(다시 저장·요약하지 않음),"
                    + " 발급에 실패한(failed)·정리된(expired) 세션도 저장하지 않고 그대로. 본인 세션만(남의 세션·없는 세션 404 NOT_FOUND),"
                    + " 기능 꺼짐 404 FEATURE_DISABLED, 입력 형식 400 VALIDATION(usage는 JSON 객체 16KB까지)")
    @PostMapping("/api/v1/talk/sessions/{id}/end")
    public TalkSessionResultResponse endTalkSession(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Positive long id,
            @Valid @RequestBody TalkSessionEndRequest request) {
        TalkEndService.EndCommand command = new TalkEndService.EndCommand(
                request.durationSec(),
                TalkEndReason.ofCode(request.endReason())
                        .orElseThrow(() -> new IllegalStateException("endReason 검증과 enum이 어긋났다")),
                request.turns().stream()
                        .map(turn -> new TalkTranscript.RawTurn(
                                TalkRole.ofCode(turn.role())
                                        .orElseThrow(() -> new IllegalStateException("role 검증과 enum이 어긋났다")),
                                turn.text()))
                        .toList(),
                usageJson(request.usage()));
        return response(ends.end(Principals.academyId(jwt), Principals.userId(jwt), id, command));
    }

    @Operation(
            summary = "프리토킹 세션 조회",
            description = "종료 응답과 같은 모양. 종료 전이면 durationSec·studentTurnCount·summary null, 종료 직후 요약 중이면 summary null."
                    + " 본인 세션만(남의 세션·없는 세션 404 NOT_FOUND), 기능 꺼짐 404 FEATURE_DISABLED")
    @GetMapping("/api/v1/talk/sessions/{id}")
    public TalkSessionResultResponse getTalkSession(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive long id) {
        return response(ends.find(Principals.academyId(jwt), Principals.userId(jwt), id));
    }

    /** JSON 객체만, 16KB까지. 없거나 null이면 null */
    private String usageJson(JsonNode usage) {
        if (usage == null || usage.isNull()) {
            return null;
        }
        if (!usage.isObject()) {
            throw ApiException.validation("usage", "사용량은 JSON 객체여야 해요");
        }
        String text = json.writeValueAsString(usage);
        if (text.getBytes(StandardCharsets.UTF_8).length > MAX_USAGE_BYTES) {
            throw ApiException.validation("usage", "사용량이 너무 커요");
        }
        return text;
    }

    private static TalkSessionResultResponse response(TalkEndService.Result result) {
        TalkSummary summary = result.summary();
        return new TalkSessionResultResponse(
                result.id(),
                result.status().code(),
                result.durationSec(),
                result.studentTurnCount(),
                summary == null
                        ? null
                        : new TalkSessionResultResponse.Summary(
                                summary.goodExpressions(),
                                summary.corrections().stream()
                                        .map(c -> new TalkSessionResultResponse.Correction(
                                                c.said(), c.better(), c.note()))
                                        .toList()));
    }
}
