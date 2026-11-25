package com.jglearning.jg4seasons.talk;

import com.jglearning.jg4seasons.clazz.api.StudentLessonGate;
import com.jglearning.jg4seasons.infra.web.error.ApiException;
import com.jglearning.jg4seasons.infra.web.error.CommonErrorCode;
import com.jglearning.jg4seasons.talk.TalkAccess.TalkContext;
import com.jglearning.jg4seasons.talk.persistence.TalkSessionStore;
import com.jglearning.jg4seasons.talk.persistence.TalkSessionStore.NewTalkSession;
import com.jglearning.jg4seasons.talk.persistence.TalkSessionStore.TalkSessionRow;
import com.jglearning.jg4seasons.talk.prompt.TalkPrompts;
import com.jglearning.jg4seasons.talk.realtime.IssuedSecret;
import com.jglearning.jg4seasons.talk.realtime.RealtimeSessionIssuer;
import com.jglearning.jg4seasons.talk.realtime.RealtimeSessionRequest;
import com.jglearning.jg4seasons.talk.realtime.RealtimeUnavailableException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * 세션 생성(계약 D-2). 순서: 기능(404) → 같은 clientSessionId(있으면 그 세션) → 구독(403) → 반·사용 기간(404·403) → 하루 3회(429) → 지시문 조립 → 임시 키
 * 발급(503) → 행 저장.
 *
 * <p>트랜잭션을 열지 않는다: 외부 발급을 기다리는 동안 DB 연결·잠금을 잡지 않으려고 발급 뒤에 INSERT 한 문장으로 저장한다. 그래서 서로 다른 clientSessionId로 동시에 4번
 * 보내면 3회를 넘을 수 있다(1단계는 받아들임 — 서버 쪽 사용량 집계 이슈에서). 같은 clientSessionId 동시 요청은 UNIQUE가 하나만 통과시키고, 진 쪽은 그 행을 다시 읽어
 * 이미 받은 키로 200을 준다. 멱등 키 = clientSessionId
 */
@Service
public class TalkSessionService {

    static final int DAILY_LIMIT = 3;

    static final String CREATED = "created";
    static final String FAILED = "failed";

    /**
     * 만든 세션. realtime은 created일 때만 있고(이미 끝난 세션을 다시 보내면 null — 사용자 11/25 A), created = true면 새 세션(201), false면 재전송(200)
     *
     * @param closingInstruction 시간이 다 됐을 때 브라우저가 세션에 넣는 마무리 신호(추가 지시 가-2). realtime이 null이면 null
     */
    public record Result(
            long id,
            String status,
            int maxSeconds,
            IssuedSecret realtime,
            String closingInstruction,
            boolean created) {}

    private final TalkAccess access;
    private final TalkSessionStore store;
    private final StudentLessonGate gate;
    private final TalkPrompts prompts;
    private final RealtimeSessionIssuer issuer;
    private final TalkProperties properties;
    private final Clock clock;

    TalkSessionService(
            TalkAccess access,
            TalkSessionStore store,
            StudentLessonGate gate,
            TalkPrompts prompts,
            RealtimeSessionIssuer issuer,
            TalkProperties properties,
            Clock clock) {
        this.access = access;
        this.store = store;
        this.gate = gate;
        this.prompts = prompts;
        this.issuer = issuer;
        this.properties = properties;
        this.clock = clock;
    }

    public Result create(long academyId, long studentId, String clientId, TalkTopic topic, TalkVoice voice) {
        access.requireEnabled(academyId);
        Optional<TalkSessionRow> existing = store.findByClientId(clientId);
        if (existing.isPresent()) {
            return replay(academyId, studentId, existing.get());
        }
        TalkContext context = requireStartable(academyId, studentId);
        if (store.countIssuedSince(studentId, access.startOfToday(academyId)) >= DAILY_LIMIT) {
            throw new ApiException(CommonErrorCode.DAILY_LIMIT, "프리토킹은 하루에 " + DAILY_LIMIT + "번까지 할 수 있어요");
        }

        String model = properties.realtime().model();
        NewTalkSession row = new NewTalkSession(
                clientId,
                studentId,
                context.lessonId().orElse(null),
                context.level().levelCode(),
                topic.code(),
                voice.code(),
                CREATED,
                properties.maxSeconds(),
                model,
                TalkPrompts.VERSION);
        IssuedSecret secret;
        try {
            secret = issuer.issue(request(context, topic, voice));
        } catch (RealtimeUnavailableException e) {
            recordFailure(row);
            throw new ApiException(TalkErrorCode.REALTIME_UNAVAILABLE);
        }
        try {
            long id = store.insert(row, now());
            return new Result(id, CREATED, row.maxSeconds(), secret, prompts.closing(), true);
        } catch (DuplicateKeyException e) {
            if (!TalkSessionStore.isClientIdConflict(e)) {
                throw e;
            }
            // 같은 clientSessionId가 먼저 저장됐다 — 그 세션에 방금 받은 키를 붙여 재전송처럼(새로 발급하지 않음)
            TalkSessionRow winner = store.findByClientId(clientId).orElseThrow(() -> e);
            requireOwner(winner, studentId);
            return new Result(winner.id(), winner.status(), winner.maxSeconds(), secret, prompts.closing(), false);
        }
    }

    /** 같은 clientSessionId 재전송: 본인 세션이고 created면 키만 새로(구독·사용 기간은 다시 확인), 끝난 세션이면 realtime 없이 */
    private Result replay(long academyId, long studentId, TalkSessionRow row) {
        requireOwner(row, studentId);
        if (!CREATED.equals(row.status())) {
            return new Result(row.id(), row.status(), row.maxSeconds(), null, null, false);
        }
        TalkContext context = requireStartable(academyId, studentId);
        TalkTopic topic =
                TalkTopic.ofCode(row.topic()).orElseThrow(() -> new IllegalStateException("세션의 주제가 잘못됐다: " + row.id()));
        TalkVoice voice =
                TalkVoice.ofCode(row.voice()).orElseThrow(() -> new IllegalStateException("세션의 음성이 잘못됐다: " + row.id()));
        IssuedSecret secret;
        try {
            secret = issuer.issue(request(context, topic, voice));
        } catch (RealtimeUnavailableException e) {
            throw new ApiException(TalkErrorCode.REALTIME_UNAVAILABLE);
        }
        return new Result(row.id(), row.status(), row.maxSeconds(), secret, prompts.closing(), false);
    }

    /** 구독(403) → 반(404)·사용 기간(403) */
    private TalkContext requireStartable(long academyId, long studentId) {
        if (!access.entitled(academyId)) {
            throw new ApiException(TalkErrorCode.SUBSCRIPTION_REQUIRED);
        }
        TalkContext context = access.context(academyId, studentId);
        gate.requireLicenseActive(academyId, context.clazz().bookId());
        return context;
    }

    private RealtimeSessionRequest request(TalkContext context, TalkTopic topic, TalkVoice voice) {
        TalkProperties.Realtime realtime = properties.realtime();
        return new RealtimeSessionRequest(
                realtime.model(),
                prompts.instructions(context.level(), topic, context.english(), List.of()),
                realtime.voiceFor(voice),
                realtime.transcriptionModel(),
                realtime.keyTtlSeconds());
    }

    /** 발급 실패를 failed 행으로 남긴다(한도에는 안 셈). 같은 clientSessionId가 이미 있으면 남기지 않는다 */
    private void recordFailure(NewTalkSession row) {
        NewTalkSession failed = new NewTalkSession(
                row.clientId(),
                row.studentId(),
                row.lessonId(),
                row.levelCode(),
                row.topic(),
                row.voice(),
                FAILED,
                row.maxSeconds(),
                row.model(),
                row.promptVersion());
        try {
            store.insert(failed, now());
        } catch (DuplicateKeyException e) {
            if (!TalkSessionStore.isClientIdConflict(e)) {
                throw e;
            }
        }
    }

    /** 남이 쓴 clientSessionId — 그 세션이 있다는 것 말고는 드러내지 않는다(제출 API와 같은 400) */
    private static void requireOwner(TalkSessionRow row, long studentId) {
        if (row.studentId() != studentId) {
            throw ApiException.validation("clientSessionId", "이미 쓰인 대화 번호예요");
        }
    }

    /** DATETIME은 소수 초를 반올림한다 — 내림해 실제보다 늦은 시각(다음 날)이 되지 않게 */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.SECONDS);
    }
}
