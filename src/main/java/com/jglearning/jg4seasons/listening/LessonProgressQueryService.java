package com.jglearning.jg4seasons.listening;

import com.jglearning.jg4seasons.auth.api.AcademyStudents;
import com.jglearning.jg4seasons.content.api.LessonContent;
import com.jglearning.jg4seasons.listening.api.LessonProgressQuery;
import com.jglearning.jg4seasons.listening.api.LessonProgressSummary;
import com.jglearning.jg4seasons.listening.api.ListeningDetail;
import com.jglearning.jg4seasons.listening.api.ListeningDetailQuery;
import com.jglearning.jg4seasons.listening.api.PassageProgressSummary;
import com.jglearning.jg4seasons.listening.api.TrackProgressQuery;
import com.jglearning.jg4seasons.listening.api.TrackProgressSummary;
import com.jglearning.jg4seasons.listening.completion.CompletionRule;
import com.jglearning.jg4seasons.listening.completion.CompletionRuleEvaluator;
import com.jglearning.jg4seasons.listening.passage.PassageSummaries;
import com.jglearning.jg4seasons.listening.passage.PassageSummary;
import com.jglearning.jg4seasons.listening.passage.StoredPassage;
import com.jglearning.jg4seasons.listening.passage.TrackState;
import com.jglearning.jg4seasons.listening.persistence.ProgressStore;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class LessonProgressQueryService implements LessonProgressQuery, TrackProgressQuery, ListeningDetailQuery {

    private static final Logger log = LoggerFactory.getLogger(LessonProgressQueryService.class);

    private final ProgressStore store;

    LessonProgressQueryService(ProgressStore store) {
        this.store = store;
    }

    @Override
    public Map<Long, Map<Long, LessonProgressSummary>> progressOf(
            Collection<Long> studentIds, Collection<Long> lessonIds) {
        return store.lessonProgressOf(studentIds, lessonIds);
    }

    @Override
    public Map<Long, Map<Long, TrackProgressSummary>> trackProgressOf(
            AcademyStudents students, Collection<Long> trackIds) {
        return store.trackProgressOf(students.ids(), trackIds);
    }

    /*
     * 지문 요약은 학생 회차 상세(2.3)와 같은 PassageSummaries.of를 거친다 — 저장 행의 청취율·완료를 그대로 내보내지 않는다(바닥 규칙, 쓸 수 있는 행인지 판단).
     */
    @Override
    public ListeningDetail detailOf(AcademyStudents students, Collection<LessonContent> lessons) {
        if (students.isEmpty() || lessons.isEmpty()) {
            return new ListeningDetail(Map.of(), Map.of());
        }
        List<Long> trackIds = new ArrayList<>();
        List<Long> passageIds = new ArrayList<>();
        for (LessonContent lesson : lessons) {
            for (LessonContent.Passage passage : lesson.passages()) {
                passageIds.add(passage.id());
                passage.voices().forEach(voice -> trackIds.add(voice.trackId()));
            }
        }
        Map<Long, Map<Long, TrackProgressSummary>> tracks = store.trackProgressOf(students.ids(), trackIds);
        Map<Long, Map<Long, StoredPassage>> stored = store.passageProgressOf(students.ids(), passageIds);

        Map<Long, Map<Long, PassageProgressSummary>> passages = new HashMap<>();
        Map<String, Integer> unusable = new TreeMap<>(); // 경고 문구 → 학생 수
        for (long studentId : students.ids()) {
            Map<Long, TrackState> states = new HashMap<>();
            tracks.getOrDefault(studentId, Map.of())
                    .forEach((trackId, track) ->
                            states.put(trackId, new TrackState(track.coverage(), track.completed())));
            Map<Long, StoredPassage> rows = stored.getOrDefault(studentId, Map.of());
            Map<Long, PassageProgressSummary> mine = new HashMap<>();
            for (LessonContent lesson : lessons) {
                CompletionRule rule = CompletionRule.parse(
                        lesson.rule().coverageThreshold(), lesson.rule().requiredTracksJson());
                for (LessonContent.Passage passage : lesson.passages()) {
                    Optional<StoredPassage> row = Optional.ofNullable(rows.get(passage.id()));
                    PassageSummary summary = PassageSummaries.of(passage, rule, row, states);
                    if (rule.combineVoices()
                            && row.isPresent()
                            && PassageSummaries.combined(passage, rule, row).isEmpty()) {
                        unusable.merge(
                                "lesson " + lesson.code() + " 지문 " + passage.no() + " 저장 기준 track "
                                        + row.get().refTrackId() + " "
                                        + row.get().refDurationMs() + "ms",
                                1,
                                Integer::sum);
                    }
                    mine.put(passage.id(), new PassageProgressSummary(summary.coverage(), summary.completed()));
                }
            }
            passages.put(studentId, Map.copyOf(mine));
        }
        // 콘텐츠를 다시 넣어 기준 트랙의 길이가 바뀌었거나 규칙의 필수 목소리가 바뀌었다(학생 화면 2.3과 같은 경고). 완료한 학생은 그 회차를 다시 열지 않아, 이 상태를 보는
        // 것이 교사 화면뿐일 수 있다. 학생마다 찍지 않고 (회차, 지문, 저장 기준)마다 한 줄
        unusable.forEach((where, count) ->
                log.warn("지문 집계의 기준이 지금과 다르다: {} — 학생 {}명, 목소리별 집계로 보낸다(다음 flush가 다시 계산)", where, count));
        return new ListeningDetail(tracks, Map.copyOf(passages));
    }

    @Override
    public boolean isRequired(LessonContent.Rule rule, String voice) {
        return CompletionRuleEvaluator.isRequired(
                CompletionRule.parse(rule.coverageThreshold(), rule.requiredTracksJson()), voice);
    }
}
