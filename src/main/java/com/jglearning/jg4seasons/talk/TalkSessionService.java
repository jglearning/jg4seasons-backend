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
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
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

    private static final Logger log = LoggerFactory.getLogger(TalkSessionService.class);

    static final int DAILY_LIMIT = 3;

    /**
     * 만든 세션. 연결 정보는 status가 created일 때만 있다(끝난 세션을 다시 보내면 null — 사용자 11/25 A). created = true면 새 세션(201), false면
     * 재전송(200). 짝(created ↔ 연결 정보)은 생성자가 확인하고, 만드는 곳은 팩터리 셋(created·reissued·closed)만 쓴다
     */
    public record Result(long id, TalkSessionStatus status, int maxSeconds, Connection connection, boolean created) {

        /**
         * 브라우저가 모델에 연결할 재료. 임시 키와 마무리 신호는 늘 함께
         *
         * @param closingInstruction 시간이 다 됐을 때 브라우저가 세션에 넣는 마무리 신호(추가 지시 가-2)
         */
        public record Connection(IssuedSecret secret, String closingInstruction) {

            public Connection {
                Objects.requireNonNull(secret, "secret");
                Objects.requireNonNull(closingInstruction, "closingInstruction");
            }
        }

        public Result {
            if (connection != null && status != TalkSessionStatus.CREATED) {
                throw new IllegalArgumentException("연결 정보는 created 세션에만: " + status);
            }
            if (created && connection == null) {
                throw new IllegalArgumentException("새 세션에는 연결 정보가 있어야 한다");
            }
        }

        static Result created(long id, int maxSeconds, Connection connection) {
            return new Result(id, TalkSessionStatus.CREATED, maxSeconds, connection, true);
        }

        /** 같은 clientSessionId 재전송으로 키만 새로 */
        static Result reissued(long id, int maxSeconds, Connection connection) {
            return new Result(id, TalkSessionStatus.CREATED, maxSeconds, connection, false);
        }

        /** 더 연결할 수 없는 세션(끝남·실패·재발급 기간 지남) */
        static Result closed(long id, TalkSessionStatus status, int maxSeconds) {
            return new Result(id, status, maxSeconds, null, false);
        }
    }

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

    public Result create(long academyId, long studentId, String clientId, TalkTopic topic) {
        access.requireEnabled(academyId);
        Optional<TalkSessionRow> existing = store.findByClientId(clientId);
        if (existing.isPresent()) {
            return replay(academyId, studentId, existing.get());
        }
        TalkContext context = requireStartable(academyId, studentId);
        // 목소리는 반의 레벨 캐릭터 성별로(#86 — 학생이 고르지 않음)
        TalkVoice voice = context.level().voice();
        if (store.countIssuedSince(studentId, access.startOfToday(academyId)) >= DAILY_LIMIT) {
            throw new ApiException(CommonErrorCode.DAILY_LIMIT, "프리토킹은 하루에 " + DAILY_LIMIT + "번까지 할 수 있어요");
        }

        String model = properties.realtime().model();
        NewTalkSession row = new NewTalkSession(
                clientId,
                studentId,
                context.lessonId().orElse(null),
                context.level().levelCode(),
                topic,
                voice,
                TalkSessionStatus.CREATED,
                properties.maxSeconds(),
                model,
                TalkPrompts.VERSION);
        IssuedSecret secret;
        try {
            secret = issuer.issue(request(context, topic, voice));
        } catch (RealtimeUnavailableException e) {
            // 실패 기록은 덤 — 기록이 깨져도 503은 지키고, 둘 다 로그에 남게(리뷰 5·6). 원인 메시지에는 키·본문이 없다
            try {
                recordFailure(row);
            } catch (DataAccessException recordError) {
                e.addSuppressed(recordError);
            }
            throw new ApiException(TalkErrorCode.REALTIME_UNAVAILABLE, null, e);
        }
        try {
            long id = store.insert(row, now());
            return Result.created(id, row.maxSeconds(), connection(secret));
        } catch (DuplicateKeyException e) {
            if (!TalkSessionStore.isClientIdConflict(e)) {
                throw e;
            }
            // 같은 clientSessionId가 먼저 저장됐다 — created면 방금 받은 키를 붙여 재전송처럼(새로 발급하지 않음), 아니면(발급 실패 등) 키를 버리고
            // 끝난 세션처럼. failed 행은 한도에 안 잡히므로 키를 주면 셈에 없는 대화가 생긴다(리뷰 4)
            TalkSessionRow winner = store.findByClientId(clientId).orElseThrow(() -> e);
            requireOwner(winner, studentId);
            if (winner.status() != TalkSessionStatus.CREATED) {
                return Result.closed(winner.id(), winner.status(), winner.maxSeconds());
            }
            return Result.reissued(winner.id(), winner.maxSeconds(), connection(secret));
        }
    }

    /**
     * 같은 clientSessionId 재전송: 본인 세션이고 created이며 만든 지 키 수명(기본 60초) 안이면 키만 새로(구독·사용 기간은 다시 확인). 끝난 세션이나 기간이 지난
     * 세션은 realtime 없이 — 재전송은 두 번 누름·네트워크 재시도용이고, 기간이 없으면 세션 하나로 한도 밖 키를 계속 받을 수 있다(리뷰 1, 사용자 11/25 A)
     */
    private Result replay(long academyId, long studentId, TalkSessionRow row) {
        requireOwner(row, studentId);
        if (row.status() != TalkSessionStatus.CREATED || reissueWindowPassed(row)) {
            return Result.closed(row.id(), row.status(), row.maxSeconds());
        }
        TalkContext context = requireStartable(academyId, studentId);
        IssuedSecret secret;
        try {
            secret = issuer.issue(request(context, row.topic(), row.voice()));
        } catch (RealtimeUnavailableException e) {
            throw new ApiException(TalkErrorCode.REALTIME_UNAVAILABLE, null, e);
        }
        return Result.reissued(row.id(), row.maxSeconds(), connection(secret));
    }

    /** 만든 시각 + 키 수명이 지났는가(경계 포함 — 60초째는 아직 받는다) */
    private boolean reissueWindowPassed(TalkSessionRow row) {
        return now().isAfter(row.createdAt().plusSeconds(properties.realtime().keyTtlSeconds()));
    }

    private Result.Connection connection(IssuedSecret secret) {
        return new Result.Connection(secret, prompts.closing());
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
        try {
            store.insert(row.withStatus(TalkSessionStatus.FAILED), now());
        } catch (DuplicateKeyException e) {
            if (!TalkSessionStore.isClientIdConflict(e)) {
                throw e;
            }
            // 같은 clientSessionId 경합(리뷰 11) — 동작은 그대로(503), 실제로 생겼는지 운영에서 보이게
            log.info("발급 실패 기록 생략: 같은 clientSessionId 세션이 이미 있음(학생 {})", row.studentId());
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
