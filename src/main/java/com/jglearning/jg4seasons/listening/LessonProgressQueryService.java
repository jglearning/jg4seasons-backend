package com.jglearning.jg4seasons.listening;

import com.jglearning.jg4seasons.auth.api.AcademyStudents;
import com.jglearning.jg4seasons.content.api.LessonContent;
import com.jglearning.jg4seasons.listening.api.LessonProgressQuery;
import com.jglearning.jg4seasons.listening.api.LessonProgressSummary;
import com.jglearning.jg4seasons.listening.api.TrackProgressQuery;
import com.jglearning.jg4seasons.listening.api.TrackProgressSummary;
import com.jglearning.jg4seasons.listening.completion.CompletionRule;
import com.jglearning.jg4seasons.listening.completion.CompletionRuleEvaluator;
import com.jglearning.jg4seasons.listening.persistence.ProgressStore;
import java.util.Collection;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class LessonProgressQueryService implements LessonProgressQuery, TrackProgressQuery {

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

    @Override
    public boolean isRequired(LessonContent.Rule rule, String voice) {
        return CompletionRuleEvaluator.isRequired(
                CompletionRule.parse(rule.coverageThreshold(), rule.requiredTracksJson()), voice);
    }
}
