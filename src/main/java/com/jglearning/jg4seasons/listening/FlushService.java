package com.jglearning.jg4seasons.listening;

import com.jglearning.jg4seasons.academy.api.AcademyDirectory;
import com.jglearning.jg4seasons.clazz.api.LessonAccess;
import com.jglearning.jg4seasons.clazz.api.StudentClass;
import com.jglearning.jg4seasons.clazz.api.StudentClasses;
import com.jglearning.jg4seasons.content.api.LessonCatalog;
import com.jglearning.jg4seasons.content.api.LessonContent;
import com.jglearning.jg4seasons.content.api.PassageTrack;
import com.jglearning.jg4seasons.infra.web.error.ApiException;
import com.jglearning.jg4seasons.infra.web.error.CommonErrorCode;
import com.jglearning.jg4seasons.listening.completion.CompletionRule;
import com.jglearning.jg4seasons.listening.completion.CompletionRuleEvaluator;
import com.jglearning.jg4seasons.listening.persistence.FlushStore;
import com.jglearning.jg4seasons.listening.persistence.FlushStore.SessionRow;
import com.jglearning.jg4seasons.listening.persistence.FlushStore.TrackProgressLock;
import com.jglearning.jg4seasons.listening.persistence.FlushStore.TrackProgressUpdate;
import com.jglearning.jg4seasons.listening.persistence.ProgressStore;
import com.jglearning.jg4seasons.listening.persistence.ProgressStore.LessonProgressRow;
import com.jglearning.jg4seasons.listening.persistence.ProgressStore.TrackProgressRow;
import com.jglearning.jg4seasons.listening.segment.MergedRanges;
import com.jglearning.jg4seasons.listening.segment.Range;
import com.jglearning.jg4seasons.listening.segment.SegmentFilter;
import com.jglearning.jg4seasons.listening.segment.SegmentFilter.Accepted;
import com.jglearning.jg4seasons.listening.segment.SegmentFilter.IncomingSegment;
import com.jglearning.jg4seasons.listening.web.dto.FlushResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 3.1 청취 구간 전송(TRK-04·05·06). 한 트랜잭션: 학원 활성 → 트랙 → 현재 반·권·열림(2.3과 같은 판정) → 세션 upsert·검증 → 세그먼트 걸러 추가 → (새 세그먼트가
 * 있으면) 회차 행 잠금 → 트랙 행 잠금 → 병합·청취율·완료 → 다른 트랙 잠금 읽기 → 회차 재평가.
 *
 * <p>잠금 순서는 회차 → 트랙 → 다른 트랙(FOR SHARE). 회차 행을 먼저 잡아 같은 학생·같은 회차 flush를 줄 세운다. 트랙 행을 먼저 잡으면 서로 다른 지문을 동시에
 * 보낼 때 서로의 트랙을 기다리는 교착이 생기고, 다른 트랙을 일반 SELECT로 읽으면 REPEATABLE READ 스냅샷이라 방금 끝난 다른 지문 완료를 놓친다.
 *
 * <p>멱등: 같은 세션의 같은 구간은 다시 저장·합산하지 않고(SegmentFilter), 병합은 순서·중복과 무관(MergedRanges). 완료는 한 번 되면 유지(결정 E).
 */
@Service
public class FlushService {

    /** 요청 값. 형식(UUID 등)은 컨트롤러가 400으로 확인하고, 필수 값의 null은 여기서도 막는다. 세그먼트 원소의 null은 버릴 대상이라 허용 */
    public record FlushCommand(
            String clientSessionId, long trackId, Instant startedAt, String userAgent, List<IncomingSegment> segments) {
        public FlushCommand {
            Objects.requireNonNull(clientSessionId, "clientSessionId");
            Objects.requireNonNull(startedAt, "startedAt");
            Objects.requireNonNull(segments, "segments");
            segments = Collections.unmodifiableList(new ArrayList<>(segments));
        }
    }

    private static final String NOT_FOUND = "회차를 찾을 수 없어요";

    private final AcademyDirectory academies;
    private final StudentClasses studentClasses;
    private final LessonCatalog catalog;
    private final FlushStore store;
    private final ProgressStore progress;
    private final Clock clock;

    FlushService(
            AcademyDirectory academies,
            StudentClasses studentClasses,
            LessonCatalog catalog,
            FlushStore store,
            ProgressStore progress,
            Clock clock) {
        this.academies = academies;
        this.studentClasses = studentClasses;
        this.catalog = catalog;
        this.store = store;
        this.progress = progress;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public FlushResponse flush(long academyId, long studentId, FlushCommand command) {
        if (!academies.isActive(academyId)) {
            throw new ApiException(CommonErrorCode.UNAUTHORIZED);
        }
        PassageTrack track = catalog.findPassageTrack(command.trackId())
                .orElseThrow(() -> new ApiException(CommonErrorCode.NOT_FOUND, NOT_FOUND));
        StudentClass clazz = studentClasses
                .currentClass(academyId, studentId)
                .filter(found -> found.bookId() == track.bookId())
                .orElseThrow(() -> new ApiException(CommonErrorCode.NOT_FOUND, NOT_FOUND));
        LocalDate today = LocalDate.now(clock.withZone(academies.zoneOf(academyId)));
        LessonAccess access = studentClasses.access(clazz, track.lessonId(), today);
        if (access == LessonAccess.NOT_IN_CLASS) {
            throw new ApiException(CommonErrorCode.NOT_FOUND, NOT_FOUND);
        }
        if (access == LessonAccess.LOCKED) {
            throw new ApiException(ListeningErrorCode.LESSON_LOCKED, "수업일이 되면 열려요");
        }

        Instant now = clock.instant();
        SessionRow session = store.upsertSession(
                command.clientSessionId(), studentId, track.trackId(), command.startedAt(), command.userAgent(), now);
        if (session.studentId() != studentId || session.trackId() != track.trackId()) {
            throw ApiException.validation("clientSessionId", "다른 재생에서 쓴 세션이에요. 새 세션으로 보내 주세요");
        }
        SegmentFilter.Result filtered =
                SegmentFilter.accept(command.segments(), track.durationMs(), store.receivedRanges(session.id()));

        LessonContent lesson = catalog.findByCode(track.lessonCode())
                .orElseThrow(() -> new IllegalStateException("트랙의 회차가 없다: " + track.lessonCode()));
        CompletionRule rule = CompletionRule.parse(
                lesson.rule().coverageThreshold(), lesson.rule().requiredTracksJson());
        if (filtered.accepted().isEmpty()) {
            return current(studentId, track, lesson, filtered.dropped());
        }
        store.insertSegments(session.id(), filtered.accepted());

        LessonProgressRow lessonLock = store.lockLessonProgress(
                studentId, lesson.lessonId(), lesson.passages().size());
        TrackProgressLock trackLock = store.lockTrackProgress(studentId, track.trackId(), now);
        TrackResult trackNow = updateTrack(studentId, track, rule, trackLock, filtered.accepted(), now);
        LessonProgressRow lessonNow =
                updateLesson(studentId, lesson, rule, lessonLock, track.trackId(), trackNow.completed(), now);

        return new FlushResponse(
                new FlushResponse.Track(track.trackId(), plain(trackNow.coverage()), trackNow.completed()),
                lesson(lesson, lessonNow),
                filtered.dropped());
    }

    /** 갱신한 트랙 집계(응답용) */
    private record TrackResult(BigDecimal coverage, boolean completed) {}

    private TrackResult updateTrack(
            long studentId,
            PassageTrack track,
            CompletionRule rule,
            TrackProgressLock lock,
            List<Accepted> accepted,
            Instant now) {
        List<Range> added = accepted.stream().map(Accepted::range).toList();
        List<Range> merged = MergedRanges.merge(lock.merged(), added, track.durationMs());
        int covered = MergedRanges.coveredMs(merged);
        BigDecimal coverage = MergedRanges.coverage(covered, track.durationMs());
        Instant completedAt = lock.completedAt() != null
                ? lock.completedAt()
                : CompletionRuleEvaluator.isTrackComplete(rule, coverage) ? now : null;
        // 정직성 지표(TRK-05): 반복은 세되 트랙 길이를 넘는 부분(받기만 하는 1초 여유)은 세지 않는다 — 끝부분만 반복 전송해 부풀리지 못하게
        int played = added.stream()
                .mapToInt(range -> Math.max(0, Math.min(range.endMs(), track.durationMs()) - range.startMs()))
                .sum();
        store.updateTrackProgress(
                studentId,
                track.trackId(),
                new TrackProgressUpdate(
                        merged,
                        covered,
                        coverage,
                        completedAt,
                        lock.totalPlayMs() + played,
                        store.sessionCount(studentId, track.trackId()),
                        now));
        return new TrackResult(coverage, completedAt != null);
    }

    /** 지문마다 필수 속도 트랙 중 하나라도 완료면 그 지문 완료. 회차 completed는 한 번 되면 유지(결정 E) */
    private LessonProgressRow updateLesson(
            long studentId,
            LessonContent lesson,
            CompletionRule rule,
            LessonProgressRow lock,
            long currentTrackId,
            boolean currentCompleted,
            Instant now) {
        Set<Long> requiredTrackIds = new HashSet<>();
        lesson.passages()
                .forEach(passage -> passage.speeds().stream()
                        .filter(speed -> CompletionRuleEvaluator.isRequired(rule, speed.speed()))
                        .forEach(speed -> requiredTrackIds.add(speed.trackId())));
        Set<Long> others = new HashSet<>(requiredTrackIds);
        others.remove(currentTrackId);
        Set<Long> completed = new HashSet<>(store.completedTracksForShare(studentId, others));
        if (currentCompleted) {
            completed.add(currentTrackId);
        }
        int total = lesson.passages().size();
        int done = (int) lesson.passages().stream()
                .filter(passage -> passage.speeds().stream()
                        .filter(speed -> CompletionRuleEvaluator.isRequired(rule, speed.speed()))
                        .anyMatch(speed -> completed.contains(speed.trackId())))
                .count();
        LessonProgressStatus status;
        Instant completedAt = null;
        if (lock.status() == LessonProgressStatus.COMPLETED) {
            status = LessonProgressStatus.COMPLETED;
            done = Math.min(Math.max(done, lock.passagesDone()), total);
        } else if (total > 0 && done == total) {
            status = LessonProgressStatus.COMPLETED;
            completedAt = now;
        } else {
            status = LessonProgressStatus.IN_PROGRESS;
        }
        store.updateLessonProgress(studentId, lesson.lessonId(), status, done, total, completedAt);
        return new LessonProgressRow(status, done, total);
    }

    /** 새로 받은 세그먼트가 없으면 집계를 건드리지 않고 지금 값을 돌려준다(행을 만들거나 잠그지 않음) */
    private FlushResponse current(long studentId, PassageTrack track, LessonContent lesson, int dropped) {
        TrackProgressRow heard =
                progress.trackProgress(studentId, List.of(track.trackId())).get(track.trackId());
        LessonProgressRow lessonRow = progress.lessonProgress(studentId, lesson.lessonId())
                .orElse(new LessonProgressRow(
                        LessonProgressStatus.NOT_STARTED, 0, lesson.passages().size()));
        return new FlushResponse(
                new FlushResponse.Track(
                        track.trackId(),
                        heard == null ? BigDecimal.ZERO : plain(heard.coverage()),
                        heard != null && heard.completed()),
                lesson(lesson, lessonRow),
                dropped);
    }

    private static FlushResponse.Lesson lesson(LessonContent lesson, LessonProgressRow row) {
        return new FlushResponse.Lesson(lesson.code(), row.status().value(), row.passagesDone(), row.passagesTotal());
    }

    /** 끝 0을 뗀다(0.4615 그대로, 0.5000 → 0.5, 0.0000 → 0) */
    private static BigDecimal plain(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }
}
