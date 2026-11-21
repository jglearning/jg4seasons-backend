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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class LessonProgressQueryService implements LessonProgressQuery, TrackProgressQuery, ListeningDetailQuery {

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
                    PassageSummary summary =
                            PassageSummaries.of(passage, rule, Optional.ofNullable(rows.get(passage.id())), states);
                    mine.put(passage.id(), new PassageProgressSummary(summary.coverage(), summary.completed()));
                }
            }
            passages.put(studentId, Map.copyOf(mine));
        }
        return new ListeningDetail(tracks, Map.copyOf(passages));
    }

    @Override
    public boolean isRequired(LessonContent.Rule rule, String voice) {
        return CompletionRuleEvaluator.isRequired(
                CompletionRule.parse(rule.coverageThreshold(), rule.requiredTracksJson()), voice);
    }
}
