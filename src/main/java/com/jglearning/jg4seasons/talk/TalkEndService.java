package com.jglearning.jg4seasons.talk;

import com.jglearning.jg4seasons.infra.web.error.ApiException;
import com.jglearning.jg4seasons.infra.web.error.CommonErrorCode;
import com.jglearning.jg4seasons.talk.TalkTranscript.RawTurn;
import com.jglearning.jg4seasons.talk.TalkTranscript.Trimmed;
import com.jglearning.jg4seasons.talk.persistence.TalkSessionStore;
import com.jglearning.jg4seasons.talk.persistence.TalkSessionStore.TalkSessionRow;
import com.jglearning.jg4seasons.talk.persistence.TalkSessionStore.TalkSessionView;
import com.jglearning.jg4seasons.talk.summary.SummaryGuard;
import com.jglearning.jg4seasons.talk.summary.TalkSummarizer;
import com.jglearning.jg4seasons.talk.summary.TalkSummary;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * 세션 종료(계약 D-3)·조회(D-4). 종료 순서: 기능(404) → 본인 세션 잠금 읽기(아니면 404) → 이미 ended거나 failed면 저장된 그대로 → 자르기·종료 기록·글자 기록 저장(짧은
 * 트랜잭션, 커밋) → 트랜잭션 밖에서 요약(최대 20초) → SummaryGuard로 거른 요약 저장. 요약 실패는 summary null로 끝난다(작업 지시 A-2).
 *
 * <p>같은 세션 종료 2건이 겹치면 행 잠금으로 하나만 저장하고, 다른 쪽은 저장된 결과(요약 전이면 summary null)를 돌려준다. 멱등 키 = 세션 id
 */
@Service
public class TalkEndService {

    private static final Logger log = LoggerFactory.getLogger(TalkEndService.class);

    /**
     * 요약 저장 형식 전용 매퍼(HTTP 요청용 엄격 매퍼가 아님). 모르는 칸은 넘기고 빠진 목록은 빈 목록 — 나중에 칸을 더하거나 빼도 지난 세션 요약이 사라지지 않게(리뷰 4).
     * 형식은 TalkSessionEndTest가 고정한다
     */
    private static final JsonMapper SUMMARY_JSON = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    /** 종료 요청. usageJson은 JSON 객체 문자열 또는 null(검증은 컨트롤러) */
    public record EndCommand(int durationSec, TalkEndReason reason, List<RawTurn> turns, String usageJson) {

        public EndCommand {
            turns = List.copyOf(turns);
        }
    }

    /** 응답 재료. 종료 전이면 durationSec·studentTurnCount null, 요약이 없으면 summary null */
    public record Result(
            long id, TalkSessionStatus status, Integer durationSec, Integer studentTurnCount, TalkSummary summary) {}

    /** 이번 요청이 새로 끝냈으면 요약 재료 */
    private record Ended(TalkLevel level, Trimmed trimmed) {}

    private final TalkAccess access;
    private final TalkSessionStore store;
    private final TalkSummarizer summarizer;
    private final TransactionTemplate transactions;
    private final Clock clock;

    TalkEndService(
            TalkAccess access,
            TalkSessionStore store,
            TalkSummarizer summarizer,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.access = access;
        this.store = store;
        this.summarizer = summarizer;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public Result end(long academyId, long studentId, long sessionId, EndCommand command) {
        access.requireEnabled(academyId);
        Optional<Ended> ended = transactions.execute(status -> {
            TalkSessionRow row = store.lockOwned(sessionId, studentId).orElseThrow(TalkEndService::notFound);
            // 종료할 수 있는 건 created뿐(허용 목록 — 상태가 늘어도 기본은 "그대로", 리뷰 3). 이미 끝남(ended)·대화 없음(failed, 사용자 11/25 A)·
            // 정리됨(expired, #79에서 허용 여부를 다시 정함)은 저장하지 않고 그대로
            if (row.status() != TalkSessionStatus.CREATED) {
                return Optional.<Ended>empty();
            }
            Trimmed trimmed = TalkTranscript.trim(command.turns(), command.durationSec(), row.maxSeconds());
            store.markEnded(
                    sessionId,
                    trimmed,
                    command.reason(),
                    command.usageJson(),
                    clock.instant().truncatedTo(ChronoUnit.SECONDS));
            store.insertTurns(sessionId, trimmed.turns());
            return Optional.of(new Ended(TalkLevel.ofLevelCode(row.levelCode()), trimmed));
        });
        ended.ifPresent(done -> {
            // 종료는 이미 커밋됨 — 요약·요약 저장의 어떤 실패도 종료 응답을 막지 않는다(summary null, 리뷰 2). 내용·키 없이 종류만
            try {
                summarize(sessionId, done);
            } catch (RuntimeException e) {
                log.error(
                        "프리토킹 요약 저장 실패(종료는 완료): 세션 {} {}",
                        sessionId,
                        e.getClass().getName());
            }
        });
        return find(academyId, studentId, sessionId);
    }

    /** 본인 세션만(남의 것·없는 것 404). 기능이 꺼진 학원은 404 FEATURE_DISABLED */
    public Result find(long academyId, long studentId, long sessionId) {
        access.requireEnabled(academyId);
        TalkSessionView view = store.findOwned(sessionId, studentId).orElseThrow(TalkEndService::notFound);
        return new Result(view.id(), view.status(), view.durationSec(), view.studentTurnCount(), readSummary(view));
    }

    /** 트랜잭션 밖. 실패(요약 없음)는 그대로 null — 종료는 이미 커밋됨 */
    private void summarize(long sessionId, Ended done) {
        // 학생이 한마디도 안 했으면 요약할 것이 없다 — 모델을 부르지 않음(비용, 리뷰 6)
        if (done.trimmed().studentTurnCount() == 0) {
            return;
        }
        Optional<TalkSummary> raw =
                summarizer.summarize(done.level(), done.trimmed().turns());
        if (raw.isEmpty()) {
            return;
        }
        TalkSummary guarded = SummaryGuard.apply(raw.get(), done.trimmed().turns());
        // 검사가 버린 수(숫자만) — 프롬프트 규칙 위반이 얼마나 잦은지 보려고(리뷰 11)
        log.info(
                "프리토킹 요약 검사: 세션 {} 표현 {}→{} 고침 {}→{}",
                sessionId,
                raw.get().goodExpressions().size(),
                guarded.goodExpressions().size(),
                raw.get().corrections().size(),
                guarded.corrections().size());
        store.saveSummary(sessionId, SUMMARY_JSON.writeValueAsString(guarded));
    }

    /** 저장한 요약 JSON(우리가 쓴 모양). 읽지 못하면 데이터 오류지만 화면은 요약 없음으로 — ERROR 로그 */
    private TalkSummary readSummary(TalkSessionView view) {
        if (view.summaryJson() == null) {
            return null;
        }
        try {
            return SUMMARY_JSON.readValue(view.summaryJson(), TalkSummary.class);
        } catch (JacksonException e) {
            // 예외 메시지에 저장된 값(학생 문장)이 실릴 수 있어 종류만(리뷰 4)
            log.error("저장된 프리토킹 요약을 읽지 못했다: 세션 {} {}", view.id(), e.getClass().getSimpleName());
            return null;
        }
    }

    private static ApiException notFound() {
        return new ApiException(CommonErrorCode.NOT_FOUND, "대화를 찾을 수 없어요");
    }
}
