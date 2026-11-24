package com.jglearning.jg4seasons.wordtest;

import com.jglearning.jg4seasons.content.api.WordTestItem;
import com.jglearning.jg4seasons.content.api.WordTestItems;
import com.jglearning.jg4seasons.infra.storage.PhotoStorage;
import com.jglearning.jg4seasons.infra.storage.PhotoTooLargeException;
import com.jglearning.jg4seasons.infra.web.requestid.RequestIds;
import com.jglearning.jg4seasons.wordtest.grading.Graded;
import com.jglearning.jg4seasons.wordtest.grading.Grading;
import com.jglearning.jg4seasons.wordtest.grading.Reading;
import com.jglearning.jg4seasons.wordtest.persistence.AnswerStore;
import com.jglearning.jg4seasons.wordtest.persistence.GradingJobStore;
import com.jglearning.jg4seasons.wordtest.persistence.GradingJobStore.ClaimedJob;
import com.jglearning.jg4seasons.wordtest.persistence.SubmissionStore;
import com.jglearning.jg4seasons.wordtest.persistence.SubmissionStore.GradingTarget;
import com.jglearning.jg4seasons.wordtest.reading.AnswerSheetReader;
import com.jglearning.jg4seasons.wordtest.reading.ReadFailure;
import com.jglearning.jg4seasons.wordtest.reading.ReadResult;
import com.jglearning.jg4seasons.wordtest.reading.ReadValidation;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 채점 워커. 작업 하나를 트랜잭션 셋으로 처리한다.
 *
 * <ol>
 *   <li>선점: 작업 SKIP LOCKED → running, 제출 pending → grading(재시도면 grading 그대로) → 커밋
 *   <li>외부 호출(잠금 없음): 사진 읽기(크기 상한) → 옮겨 적기 → 검증
 *   <li>저장: 문항을 공유 잠금으로 다시 읽고 판정 → 제출 graded(이미 바뀌었으면 아무것도 안 함) → 답안 upsert → 작업 done
 * </ol>
 *
 * 실패는 30초 뒤 다시(3회까지), 사진 문제·요청 거절·설정 오류는 바로 포기. 3회째면 작업 dead·제출 failed. 로그: 실패 WARN, 포기 ERROR, 끝에 결과(graded·retry·dead)와 걸린 ms. 처리 중 로그의 requestId는 word-test-제출 id. 사진·읽은 글자·키는 남기지 않는다
 */
@Component
public class GradingWorker {

    static final int MAX_ATTEMPTS = 3;
    static final Duration RETRY_DELAY = Duration.ofSeconds(30);
    /** API 이미지 상한(base64 길이로 비교) */
    static final long API_IMAGE_LIMIT = 5L * 1024 * 1024;

    private static final String MDC_KEY = RequestIds.MDC_KEY;

    private static final Logger log = LoggerFactory.getLogger(GradingWorker.class);

    private record Claim(ClaimedJob job, GradingTarget target) {}

    private final TransactionTemplate tx;
    private final GradingJobStore jobs;
    private final SubmissionStore submissions;
    private final AnswerStore answers;
    private final WordTestItems items;
    private final PhotoStorage photos;
    private final AnswerSheetReader reader;
    private final Clock clock;

    GradingWorker(
            PlatformTransactionManager transactions,
            GradingJobStore jobs,
            SubmissionStore submissions,
            AnswerStore answers,
            WordTestItems items,
            PhotoStorage photos,
            AnswerSheetReader reader,
            Clock clock) {
        this.tx = new TransactionTemplate(transactions);
        this.jobs = jobs;
        this.submissions = submissions;
        this.answers = answers;
        this.items = items;
        this.photos = photos;
        this.reader = reader;
        this.clock = clock;
    }

    /** 작업 하나를 처리한다. 처리했으면 1, 잡을 작업이 없거나 읽기가 꺼져 있으면 0 */
    public int runOnce() {
        if (!reader.enabled()) {
            return 0;
        }
        Claim claim = tx.execute(status -> claim());
        if (claim == null) {
            return 0;
        }
        if (claim.target() == null) {
            return 1; // 이미 채점·실패한 제출의 작업 — 선점 트랜잭션에서 done으로 정리함
        }
        long submissionId = claim.target().id();
        int attempt = claim.job().attempts() + 1;
        long started = System.nanoTime();
        String outcome = "error";
        // 이 제출을 처리하는 동안의 로그(읽기 응답 포함)에 제출 id가 붙게
        String previous = MDC.get(MDC_KEY);
        MDC.put(MDC_KEY, "word-test-" + submissionId);
        try {
            outcome = grade(claim);
        } catch (ReadFailure failure) {
            boolean retry = failure.kind().retryable() && attempt < MAX_ATTEMPTS;
            log.warn(
                    "단어시험 읽기 실패: 제출 {} 시도 {} {}({})",
                    submissionId,
                    attempt,
                    failure.getMessage(),
                    retry ? "재시도" : "포기");
            outcome = recordFailure(claim, failure.kind().retryable(), failure.getMessage());
        } catch (RuntimeException unexpected) {
            // 메시지에 키·주소가 섞일 수 있어 종류와 던진 위치만 남긴다
            String where = where(unexpected);
            log.error("단어시험 채점 중 예상 못 한 오류: 제출 {} 시도 {} {}", submissionId, attempt, where);
            outcome = recordFailure(claim, true, "UNEXPECTED: " + where);
        } finally {
            log.info(
                    "단어시험 채점: 제출 {} 시도 {} {} {}ms",
                    submissionId,
                    attempt,
                    outcome,
                    (System.nanoTime() - started) / 1_000_000);
            if (previous == null) {
                MDC.remove(MDC_KEY);
            } else {
                MDC.put(MDC_KEY, previous);
            }
        }
        return 1;
    }

    /** 예외 종류와 던진 위치(클래스.메서드:줄). 메시지는 넣지 않는다(키·주소가 섞일 수 있음) */
    static String where(Throwable e) {
        StackTraceElement[] trace = e.getStackTrace();
        String type = e.getClass().getSimpleName();
        if (trace.length == 0) {
            return type;
        }
        String className = trace[0].getClassName();
        return type + " at " + className.substring(className.lastIndexOf('.') + 1) + "." + trace[0].getMethodName()
                + ":" + trace[0].getLineNumber();
    }

    private Claim claim() {
        ClaimedJob job = jobs.claimNext(clock.instant()).orElse(null);
        if (job == null) {
            return null;
        }
        GradingTarget target;
        try {
            target = submissions.findForGrading(job.submissionId()).orElse(null);
        } catch (IllegalArgumentException unreadableRow) {
            // 제출 행을 해석하지 못함(모르는 영역·상태 값). 그대로 두면 매 틱 같은 작업을 맨 앞에서 다시 잡아 큐 전체가 멈춘다 — 이 작업만 포기
            String where = where(unreadableRow);
            log.error("단어시험 작업 {}(제출 {}) 선점 중 제출 행 해석 실패 — 이 작업은 포기: {}", job.jobId(), job.submissionId(), where);
            jobs.dead(job.jobId(), job.attempts() + 1, "CLAIM_FAILED: " + where);
            return new Claim(job, null);
        }
        if (target == null || !submissions.markGrading(job.submissionId())) {
            jobs.done(job.jobId());
            return new Claim(job, null);
        }
        return new Claim(job, target);
    }

    /** @return graded · skipped(이미 다른 결과) */
    private String grade(Claim claim) {
        GradingTarget target = claim.target();
        String section = target.section().dbValue();
        int count = items.count(target.lessonId(), section);
        if (count != target.total()) {
            // 제출 뒤 문항 import로 문항 수가 바뀜 — 새 문항 수로 채점하면 total과 어긋난다(49/48). 다시 찍어 내게
            throw new ReadFailure(ReadFailure.Kind.ITEMS_CHANGED, "제출 때 문항 " + target.total() + "개, 지금 " + count + "개");
        }
        if (target.imageKey() == null) {
            throw new ReadFailure(ReadFailure.Kind.PHOTO_MISSING, "사진 키가 비었다");
        }
        byte[] image;
        try {
            image = photos.read(target.imageKey(), SubmissionService.MAX_BYTES);
        } catch (NoSuchElementException e) {
            throw new ReadFailure(ReadFailure.Kind.PHOTO_MISSING, "사진이 없다");
        } catch (PhotoTooLargeException e) {
            throw new ReadFailure(ReadFailure.Kind.PHOTO_TOO_LARGE, "사진이 상한보다 크다");
        }
        if (base64Length(image.length) > API_IMAGE_LIMIT) {
            // API 이미지 상한(5MB)이 base64 길이 기준일 수 있어 보수적으로 — 앱은 올리기 전에 긴 변 1600px로 줄인다
            throw new ReadFailure(ReadFailure.Kind.PHOTO_TOO_LARGE, "base64로 5MB를 넘는다");
        }
        ReadResult result = reader.read(image, mediaTypeOf(target.imageKey()), count);
        List<Reading> readings = ReadValidation.check(result, count);
        return tx.execute(status -> {
            // 문항을 공유 잠금으로 다시 읽어 판정 — 그새 import가 정답을 바꾸는 것과 겹치지 않게
            List<WordTestItem> locked = items.ofLocked(target.lessonId(), section);
            if (locked.size() != readings.size()) {
                throw new ReadFailure(ReadFailure.Kind.COUNT_MISMATCH, "채점 중 문항 수가 바뀌었다");
            }
            Graded graded = Grading.grade(locked, readings);
            String outcome = "graded";
            if (submissions.markGraded(
                    target.id(), graded.score(), clock.instant(), result.model(), result.promptVersion())) {
                answers.upsertAll(target.id(), graded.answers());
            } else {
                // 읽는 사이 제출이 이미 다른 결과(graded·failed)가 됨 — 답안을 덮지 않는다
                log.warn("단어시험 제출 {}이 grading이 아니어서 결과를 저장하지 않음", target.id());
                outcome = "skipped";
            }
            if (!jobs.done(claim.job().jobId())) {
                log.warn("단어시험 작업 {}(제출 {})이 이미 running이 아님", claim.job().jobId(), target.id());
            }
            return outcome;
        });
    }

    /** 실패 기록: 재시도면 queued(30초 뒤), 아니면 작업 dead·제출 failed. 기록 자체가 실패하면 원래 실패를 남기고 다시 던진다 @return retry·dead */
    private String recordFailure(Claim claim, boolean retryable, String error) {
        long submissionId = claim.target().id();
        int attempts = claim.job().attempts() + 1;
        boolean retry = retryable && attempts < MAX_ATTEMPTS;
        try {
            tx.executeWithoutResult(status -> {
                boolean recorded = retry
                        ? jobs.retry(
                                claim.job().jobId(), attempts, clock.instant().plus(RETRY_DELAY), error)
                        : jobs.dead(claim.job().jobId(), attempts, error);
                if (!recorded) {
                    log.warn(
                            "단어시험 작업 {}(제출 {})이 이미 running이 아니어서 실패를 기록하지 않음",
                            claim.job().jobId(),
                            submissionId);
                }
                if (!retry && !submissions.markFailed(submissionId)) {
                    log.warn("단어시험 제출 {}이 pending·grading이 아니어서 failed로 바꾸지 않음", submissionId);
                }
            });
        } catch (RuntimeException e) {
            log.error("단어시험 제출 {} 실패 기록 못 함(원래 {}): {}", submissionId, error, where(e));
            throw e;
        }
        if (!retry) {
            log.error("단어시험 채점 포기: 제출 {} {}회 실패, 마지막 {}", submissionId, attempts, error);
        }
        return retry ? "retry" : "dead";
    }

    static long base64Length(long bytes) {
        return (bytes + 2) / 3 * 4;
    }

    private static String mediaTypeOf(String key) {
        return key.endsWith(".png") ? "image/png" : "image/jpeg";
    }
}
