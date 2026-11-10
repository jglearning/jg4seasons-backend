package com.jglearning.jg4seasons.teacher;

import com.jglearning.jg4seasons.auth.api.AcademyStudents;
import com.jglearning.jg4seasons.auth.api.StudentAccount;
import com.jglearning.jg4seasons.auth.api.StudentRoster;
import com.jglearning.jg4seasons.clazz.api.ClassLesson;
import com.jglearning.jg4seasons.clazz.api.TeacherClass;
import com.jglearning.jg4seasons.content.api.LessonCatalog;
import com.jglearning.jg4seasons.content.api.LessonContent;
import com.jglearning.jg4seasons.content.api.LessonSummary;
import com.jglearning.jg4seasons.infra.web.error.ApiException;
import com.jglearning.jg4seasons.infra.web.error.CommonErrorCode;
import com.jglearning.jg4seasons.listening.api.LessonProgressQuery;
import com.jglearning.jg4seasons.listening.api.LessonProgressStatus;
import com.jglearning.jg4seasons.listening.api.LessonProgressSummary;
import com.jglearning.jg4seasons.listening.api.TrackProgressQuery;
import com.jglearning.jg4seasons.listening.api.TrackProgressSummary;
import com.jglearning.jg4seasons.teacher.web.dto.StudentDetailResponse;
import com.jglearning.jg4seasons.teacher.web.dto.StudentDetailResponse.StudentDetailLesson;
import com.jglearning.jg4seasons.teacher.web.dto.StudentDetailResponse.StudentDetailStudent;
import com.jglearning.jg4seasons.teacher.web.dto.StudentDetailResponse.StudentDetailTrack;
import com.jglearning.jg4seasons.teacher.web.dto.StudentDetailResponse.StudentDetailWords;
import com.jglearning.jg4seasons.teacher.web.dto.TeacherLessonDetailResponse;
import com.jglearning.jg4seasons.teacher.web.dto.TeacherLessonDetailResponse.TeacherLessonInfo;
import com.jglearning.jg4seasons.teacher.web.dto.TeacherLessonDetailResponse.TeacherLessonRow;
import com.jglearning.jg4seasons.teacher.web.dto.TeacherLessonDetailResponse.TeacherLessonStudent;
import com.jglearning.jg4seasons.teacher.web.dto.TeacherLessonDetailResponse.TeacherLessonTrack;
import com.jglearning.jg4seasons.teacher.web.dto.TeacherLessonDetailResponse.TeacherLessonTrackResult;
import com.jglearning.jg4seasons.words.api.WordStudies;
import com.jglearning.jg4seasons.words.api.WordStudyScores;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 4.3 학생 상세(TCH-03·07)·4.4 회차 상세(TCH-04). 순서: 활성 교사·범위(TeacherScope) → 학생이 그 반 명단에 있는가(아니면 404) → 집계 읽기 — 집계는 명단에서 좁힌 묶음으로만
 * 읽는다(다른 학원 학생 진도가 새지 않게). 회차는 그 반에서 열린 것만, seq 순. 열림·학생 목록 규칙은 4.2와 같다.
 */
@Service
@Transactional(readOnly = true)
public class TeacherDetailService {

    private final TeacherScope scope;
    private final LessonCatalog catalog;
    private final LessonProgressQuery lessonProgress;
    private final TrackProgressQuery trackProgress;
    private final WordStudies wordStudies;

    TeacherDetailService(
            TeacherScope scope,
            LessonCatalog catalog,
            LessonProgressQuery lessonProgress,
            TrackProgressQuery trackProgress,
            WordStudies wordStudies) {
        this.scope = scope;
        this.catalog = catalog;
        this.lessonProgress = lessonProgress;
        this.trackProgress = trackProgress;
        this.wordStudies = wordStudies;
    }

    public StudentDetailResponse student(long academyId, long actorId, long classId, long studentId) {
        TeacherClass clazz = scope.classOf(academyId, actorId, classId).clazz();
        StudentRoster roster = scope.roster(academyId, clazz);
        if (!roster.students().contains(studentId)) {
            throw new ApiException(CommonErrorCode.NOT_FOUND, "학생을 찾을 수 없어요");
        }
        AcademyStudents one = roster.students().only(studentId);
        StudentAccount account = roster.accounts().stream()
                .filter(a -> a.id() == studentId)
                .findFirst()
                .orElseThrow();

        Map<Long, LessonSummary> summaries = scope.lessonsOf(clazz);
        List<ClassLesson> open = clazz.lessons().stream()
                .filter(ClassLesson::open)
                .sorted(Comparator.comparingInt(ClassLesson::seq))
                .toList();
        List<LessonContent> contents = new ArrayList<>();
        for (ClassLesson lesson : open) {
            String code = TeacherScope.summaryOf(summaries, clazz, lesson).code();
            contents.add(catalog.findByCode(code)
                    .orElseThrow(() -> new IllegalStateException("진도표 회차의 내용을 찾을 수 없다: " + code)));
        }
        List<Long> lessonIds = contents.stream().map(LessonContent::lessonId).toList();
        List<Long> trackIds = contents.stream()
                .flatMap(c -> c.passages().stream())
                .flatMap(p -> p.voices().stream())
                .map(LessonContent.Voice::trackId)
                .toList();
        Map<Long, TrackProgressSummary> tracks =
                trackProgress.trackProgressOf(one, trackIds).getOrDefault(studentId, Map.of());
        Map<Long, LessonProgressSummary> progress =
                lessonProgress.progressOf(one.ids(), lessonIds).getOrDefault(studentId, Map.of());
        Map<Long, WordStudyScores> words = wordStudies.scoresOf(one, lessonIds).getOrDefault(studentId, Map.of());

        List<StudentDetailLesson> lessons = contents.stream()
                .map(content -> lesson(content, summaries.get(content.lessonId()), progress, tracks, words))
                .toList();
        return new StudentDetailResponse(
                new StudentDetailStudent(
                        account.id(), account.name(), account.school(), account.grade(), account.lastLoginAt()),
                lessons);
    }

    /**
     * 4.4. 순서: 활성 교사·범위 → 회차가 그 반 권의 것인가(아니면·없으면 404 — 다른 권 회차로 그 학생들 진도를 보지 못하게) → 명단(4.2와 같은 학생, 이름 순) → 집계는
     * 명단 묶음으로만. 수업 전 회차도 보여 준다(수업 전 확인용). scheduledDate는 진도표에서 휴강이 아닌 수업일, 휴강 행만 있으면 null
     */
    public TeacherLessonDetailResponse lesson(long academyId, long actorId, long classId, String lessonCode) {
        TeacherClass clazz = scope.classOf(academyId, actorId, classId).clazz();
        LessonContent content = catalog.findByCode(lessonCode)
                .filter(c -> c.bookId() == clazz.bookId())
                .orElseThrow(() -> new ApiException(CommonErrorCode.NOT_FOUND, "회차를 찾을 수 없어요"));
        LocalDate scheduledDate = clazz.lessons().stream()
                .filter(l -> l.lessonId() == content.lessonId() && l.held())
                .map(ClassLesson::scheduledDate)
                .findFirst()
                .orElse(null);
        StudentRoster roster = scope.roster(academyId, clazz);

        List<TeacherLessonTrack> trackList = new ArrayList<>();
        for (LessonContent.Passage passage : content.passages()) {
            for (LessonContent.Voice voice : passage.voices()) {
                trackList.add(new TeacherLessonTrack(
                        voice.trackId(),
                        "passage",
                        passage.no(),
                        voice.voice(),
                        trackProgress.isRequired(content.rule(), voice.voice())));
            }
        }
        Map<Long, Map<Long, TrackProgressSummary>> heard = trackProgress.trackProgressOf(
                roster.students(),
                trackList.stream().map(TeacherLessonTrack::trackId).toList());
        Map<Long, Map<Long, LessonProgressSummary>> progress =
                lessonProgress.progressOf(roster.students().ids(), List.of(content.lessonId()));

        List<TeacherLessonRow> rows = roster.accounts().stream()
                .map(student -> {
                    Map<Long, TrackProgressSummary> mine = heard.getOrDefault(student.id(), Map.of());
                    Map<String, TeacherLessonTrackResult> byTrack = new LinkedHashMap<>();
                    for (TeacherLessonTrack track : trackList) {
                        TrackProgressSummary h = mine.get(track.trackId());
                        if (h != null) {
                            byTrack.put(
                                    String.valueOf(track.trackId()),
                                    new TeacherLessonTrackResult(h.coverage().doubleValue(), h.completed()));
                        }
                    }
                    LessonProgressSummary p =
                            progress.getOrDefault(student.id(), Map.of()).get(content.lessonId());
                    return new TeacherLessonRow(
                            new TeacherLessonStudent(student.id(), student.name()),
                            (p == null ? LessonProgressStatus.NOT_STARTED : p.status()).value(),
                            byTrack);
                })
                .toList();
        return new TeacherLessonDetailResponse(
                new TeacherLessonInfo(content.code(), content.title(), scheduledDate), trackList, rows);
    }

    /** 회차 진도가 없으면 시작 전·0/지문 수(4.2 매트릭스와 같은 기본값) */
    private StudentDetailLesson lesson(
            LessonContent content,
            LessonSummary summary,
            Map<Long, LessonProgressSummary> progress,
            Map<Long, TrackProgressSummary> tracks,
            Map<Long, WordStudyScores> words) {
        LessonProgressSummary p = progress.get(content.lessonId());
        List<StudentDetailTrack> trackRows = content.passages().stream()
                .flatMap(passage -> passage.voices().stream().map(voice -> track(content, passage, voice, tracks)))
                .toList();
        WordStudyScores w = words.get(content.lessonId());
        return new StudentDetailLesson(
                content.code(),
                content.title(),
                (p == null ? LessonProgressStatus.NOT_STARTED : p.status()).value(),
                p == null ? 0 : p.requiredDone(),
                p == null ? summary.passageCount() : p.requiredTotal(),
                trackRows,
                w == null
                        ? new StudentDetailWords(content.wordTotal(), 0, 0, 0, 0)
                        : new StudentDetailWords(
                                content.wordTotal(), w.cardsViewed(), w.memKnown(), w.recallBest(), w.spellBest()));
    }

    /** 안 들은 트랙은 coverage 0·재생 0·lastPlayedAt null */
    private StudentDetailTrack track(
            LessonContent content,
            LessonContent.Passage passage,
            LessonContent.Voice voice,
            Map<Long, TrackProgressSummary> tracks) {
        TrackProgressSummary heard = tracks.get(voice.trackId());
        return new StudentDetailTrack(
                "passage",
                passage.no(),
                voice.voice(),
                trackProgress.isRequired(content.rule(), voice.voice()),
                heard == null ? 0 : heard.coverage().doubleValue(),
                heard != null && heard.completed(),
                heard == null ? 0 : heard.totalPlayMs(),
                voice.durationMs(),
                heard == null ? 0 : heard.sessionCount(),
                heard == null ? null : heard.lastPlayedAt());
    }
}
