package com.jglearning.jg4seasons.wordtest;

import com.jglearning.jg4seasons.content.api.WordTestItem;
import com.jglearning.jg4seasons.content.api.WordTestItems;
import com.jglearning.jg4seasons.infra.storage.PhotoStorage;
import com.jglearning.jg4seasons.infra.storage.PhotoTooLargeException;
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
 * 실패는 30초 뒤 다시(3회까지), 사진 문제는 바로 포기. 3회째면 작업 dead·제출 failed. 로그에는 제출 id·시도·실패 종류·걸린 ms·토큰만(사진·글자·키 없음)
 */
@Component
public class GradingWorker {

    static final int MAX_ATTEMPTS = 3;
    static final Duration RETRY_DELAY = Duration.ofSeconds(30);

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
        long started = System.nanoTime();
        try {
            grade(claim);
        } catch (ReadFailure failure) {
            fail(claim, failure.kind().retryable(), failure.getMessage());
        } catch (RuntimeException unexpected) {
            // 메시지에 키·주소가 섞일 수 있어 종류만 남긴다
            log.error(
                    "단어시험 채점 중 예상 못 한 오류: 제출 {} 시도 {} — {}",
                    claim.target().id(),
                    claim.job().attempts() + 1,
                    unexpected.getClass().getName());
            fail(claim, true, "UNEXPECTED: " + unexpected.getClass().getSimpleName());
        } finally {
            log.info(
                    "단어시험 채점: 제출 {} 시도 {} {}ms",
                    claim.target().id(),
                    claim.job().attempts() + 1,
                    (System.nanoTime() - started) / 1_000_000);
        }
        return 1;
    }

    private Claim claim() {
        ClaimedJob job = jobs.claimNext(clock.instant()).orElse(null);
        if (job == null) {
            return null;
        }
        GradingTarget target = submissions.findForGrading(job.submissionId()).orElse(null);
        if (target == null || !submissions.markGrading(job.submissionId())) {
            jobs.done(job.jobId());
            return new Claim(job, null);
        }
        return new Claim(job, target);
    }

    private void grade(Claim claim) {
        GradingTarget target = claim.target();
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
        String section = target.section().dbValue();
        int count = items.count(target.lessonId(), section);
        ReadResult result = reader.read(image, mediaTypeOf(target.imageKey()), count);
        List<Reading> readings = ReadValidation.check(result, count);
        log.info(
                "단어시험 읽기: 제출 {} 모델 {} 토큰 입력 {} 출력 {}",
                target.id(),
                result.model(),
                result.inputTokens(),
                result.outputTokens());
        tx.executeWithoutResult(status -> {
            // 문항을 공유 잠금으로 다시 읽어 판정 — 그새 import가 정답을 바꾸는 것과 겹치지 않게
            List<WordTestItem> locked = items.ofLocked(target.lessonId(), section);
            if (locked.size() != readings.size()) {
                throw new ReadFailure(ReadFailure.Kind.COUNT_MISMATCH, "채점 중 문항 수가 바뀌었다");
            }
            Graded graded = Grading.grade(locked, readings);
            if (submissions.markGraded(
                    target.id(), graded.score(), clock.instant(), result.model(), result.promptVersion())) {
                answers.upsertAll(target.id(), graded.answers());
            }
            jobs.done(claim.job().jobId());
        });
    }

    private void fail(Claim claim, boolean retryable, String error) {
        int attempts = claim.job().attempts() + 1;
        tx.executeWithoutResult(status -> {
            if (retryable && attempts < MAX_ATTEMPTS) {
                jobs.retry(claim.job().jobId(), attempts, clock.instant().plus(RETRY_DELAY), error);
            } else {
                jobs.dead(claim.job().jobId(), attempts, error);
                submissions.markFailed(claim.target().id());
            }
        });
    }

    private static String mediaTypeOf(String key) {
        return key.endsWith(".png") ? "image/png" : "image/jpeg";
    }
}
