package com.jglearning.jg4seasons.talk.web;

import com.jglearning.jg4seasons.infra.security.Principals;
import com.jglearning.jg4seasons.talk.TalkConfigService;
import com.jglearning.jg4seasons.talk.TalkSessionService;
import com.jglearning.jg4seasons.talk.TalkTopic;
import com.jglearning.jg4seasons.talk.TalkVoice;
import com.jglearning.jg4seasons.talk.web.dto.TalkConfigResponse;
import com.jglearning.jg4seasons.talk.web.dto.TalkSessionCreateRequest;
import com.jglearning.jg4seasons.talk.web.dto.TalkSessionCreatedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 캐릭터 프리토킹 학생 API(1단계, 계약 D). 학생만. 학원·학생은 access 쿠키(JWT)에서만. 그 학원에서 기능이 꺼져 있으면 모두 404 FEATURE_DISABLED. 쓰기는 X-CSRF
 * 필요. 음성은 브라우저와 모델이 직접 주고받고 서버는 지시문을 묶은 임시 키만 준다
 */
@RestController
@RolesAllowed("STUDENT")
public class TalkController {

    private final TalkConfigService configs;
    private final TalkSessionService sessions;

    TalkController(TalkConfigService configs, TalkSessionService sessions) {
        this.configs = configs;
        this.sessions = sessions;
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
            description = "세션을 기록하고 서버가 조립한 지시문(레벨·오늘 회차 단어·주제·안전 규칙)을 묶은 임시 키를 준다."
                    + " 같은 clientSessionId를 다시 보내면 새로 만들지 않는다: 그 세션이 created이고 만든 지 60초(임시 키 수명) 안이면"
                    + " 임시 키만 새로 200, 그 밖(끝난 세션·60초 지남)은 200에 realtime null(새 clientSessionId로 다시 시작),"
                    + " 다른 학생이 쓴 값이면 400 VALIDATION."
                    + " 기능 꺼짐 404 FEATURE_DISABLED, 구독 없음 403 SUBSCRIPTION_REQUIRED, 반이 없으면 404 NOT_FOUND,"
                    + " 사용 기간 아님 403 LICENSE_INACTIVE, 하루(학원 날짜) 3회 넘으면 429 DAILY_LIMIT(발급에 실패한 세션은 세지 않음),"
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
                TalkTopic.ofCode(request.topic()).orElseThrow(),
                TalkVoice.ofCode(request.voice()).orElseThrow());
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
}
