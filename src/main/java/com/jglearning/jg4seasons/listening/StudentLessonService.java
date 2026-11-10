package com.jglearning.jg4seasons.listening;

import com.jglearning.jg4seasons.academy.api.AcademyDirectory;
import com.jglearning.jg4seasons.clazz.api.StudentLessonGate;
import com.jglearning.jg4seasons.content.api.LessonCatalog;
import com.jglearning.jg4seasons.content.api.LessonContent;
import com.jglearning.jg4seasons.infra.audio.AudioProperties;
import com.jglearning.jg4seasons.infra.web.error.ApiException;
import com.jglearning.jg4seasons.infra.web.error.CommonErrorCode;
import com.jglearning.jg4seasons.listening.api.LessonProgressStatus;
import com.jglearning.jg4seasons.listening.completion.CompletionRule;
import com.jglearning.jg4seasons.listening.completion.CompletionRuleEvaluator;
import com.jglearning.jg4seasons.listening.persistence.ProgressStore;
import com.jglearning.jg4seasons.listening.persistence.ProgressStore.LessonProgressRow;
import com.jglearning.jg4seasons.listening.persistence.ProgressStore.TrackProgressRow;
import com.jglearning.jg4seasons.listening.web.dto.LessonDetailResponse;
import com.jglearning.jg4seasons.words.api.WordStudies;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 2.3 회차 상세. 학생·학원은 JWT에서만. 순서: 학원 활성 → 회차 → 그 권의 소속 반들 중 하나라도 진도표에 있고 열렸는가(StudentLessonGate) → 집계 읽기. 진도표에 없는 회차(다른 권·다른
 * 학원)는 404, 아직 안 열린 회차는 423. 집계(track_progress·lesson_progress·word_study)는 학생이 그 학원 반 소속임을 확인한 뒤에만 읽는다.
 */
@Service
@Transactional(readOnly = true)
public class StudentLessonService {

    private static final Logger log = LoggerFactory.getLogger(StudentLessonService.class);

    private final AcademyDirectory academies;
    private final StudentLessonGate gate;
    private final LessonCatalog catalog;
    private final ProgressStore progress;
    private final WordStudies wordStudies;
    private final AudioProperties audio;

    StudentLessonService(
            AcademyDirectory academies,
            StudentLessonGate gate,
            LessonCatalog catalog,
            ProgressStore progress,
            WordStudies wordStudies,
            AudioProperties audio) {
        this.academies = academies;
        this.gate = gate;
        this.catalog = catalog;
        this.progress = progress;
        this.wordStudies = wordStudies;
        this.audio = audio;
    }

    public LessonDetailResponse detail(long academyId, long studentId, String lessonCode) {
        if (!academies.isActive(academyId)) {
            throw new ApiException(CommonErrorCode.UNAUTHORIZED);
        }
        LessonContent lesson = catalog.findByCode(lessonCode)
                .orElseThrow(() -> new ApiException(CommonErrorCode.NOT_FOUND, "회차를 찾을 수 없어요"));
        // 소속 반 중 그 회차 권의 반 전부(두 반이면 어느 반에서든): 없으면 404, 모두 잠겼으면 423
        gate.requireOpen(academyId, studentId, lesson.bookId(), lesson.lessonId());

        CompletionRule rule = CompletionRule.parse(
                lesson.rule().coverageThreshold(), lesson.rule().requiredTracksJson());
        List<Long> trackIds = lesson.passages().stream()
                .flatMap(passage -> passage.voices().stream())
                .map(LessonContent.Voice::trackId)
                .toList();
        Map<Long, TrackProgressRow> tracks = progress.trackProgress(studentId, trackIds);
        LessonProgressRow lessonProgress = lessonProgress(studentId, lesson);

        return new LessonDetailResponse(
                new LessonDetailResponse.Lesson(lesson.code(), lesson.number(), lesson.title(), lesson.type()),
                new LessonDetailResponse.Level(
                        lesson.level().code(),
                        lesson.level().nameKo(),
                        lesson.level().colorHex()),
                new LessonDetailResponse.Completion(
                        lessonProgress.status().value(),
                        lessonProgress.passagesDone(),
                        lessonProgress.passagesTotal(),
                        plain(rule.threshold())),
                lesson.passages().stream()
                        .map(passage -> passage(passage, rule, tracks))
                        .toList(),
                new LessonDetailResponse.Words(lesson.wordTotal(), wordStudies.memKnown(studentId, lesson.lessonId())));
    }

    /**
     * 회차 집계. 없으면 시작 전. 집계의 지문 수가 실제와 다르면(회차 내용을 다시 넣은 뒤 flush 전) 실제 지문 수를 쓰고 WARN — 다음 flush가 다시 계산한다.
     * 500으로 막으면 학생이 그 회차를 듣지 못한다(리뷰 9, 결정 b)
     */
    private LessonProgressRow lessonProgress(long studentId, LessonContent lesson) {
        int total = lesson.passages().size();
        LessonProgressRow stored =
                progress.lessonProgress(studentId, lesson.lessonId()).orElse(null);
        if (stored == null) {
            return new LessonProgressRow(LessonProgressStatus.NOT_STARTED, 0, total);
        }
        if (stored.passagesTotal() == total) {
            return stored;
        }
        log.warn(
                "회차 집계의 지문 수가 실제와 다르다: lesson {} 집계 {} 실제 {} — 실제 지문 수로 보낸다",
                lesson.code(),
                stored.passagesTotal(),
                total);
        return new LessonProgressRow(stored.status(), Math.min(stored.passagesDone(), total), total);
    }

    private LessonDetailResponse.Passage passage(
            LessonContent.Passage passage, CompletionRule rule, Map<Long, TrackProgressRow> tracks) {
        return new LessonDetailResponse.Passage(
                passage.no(),
                passage.title(),
                passage.wordCount(),
                passage.sentences().stream()
                        .map(s -> new LessonDetailResponse.Sentence(s.no(), s.text(), s.speaker()))
                        .toList(),
                passage.voices().stream()
                        .map(voice -> voice(voice, rule, tracks.get(voice.trackId())))
                        .toList());
    }

    /** heard가 null이면 아직 안 들은 트랙 */
    private LessonDetailResponse.Voice voice(LessonContent.Voice voice, CompletionRule rule, TrackProgressRow heard) {
        return new LessonDetailResponse.Voice(
                voice.trackId(),
                voice.voice(),
                audio.urlOf(voice.fileKey()),
                voice.durationMs(),
                CompletionRuleEvaluator.isRequired(rule, voice.voice()),
                heard == null ? BigDecimal.ZERO : plain(heard.coverage()),
                heard != null && heard.completed(),
                voice.timings().stream()
                        .map(t -> new int[] {t.startMs(), t.endMs()})
                        .toList(),
                voice.timingsApproximate(),
                heard == null
                        ? List.of()
                        : heard.mergedRanges().stream()
                                .map(r -> new int[] {r.startMs(), r.endMs()})
                                .toList());
    }

    /** 0.9400 → 0.94, 0.000 → 0 (JSON 숫자를 짧게) */
    private static BigDecimal plain(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }
}
