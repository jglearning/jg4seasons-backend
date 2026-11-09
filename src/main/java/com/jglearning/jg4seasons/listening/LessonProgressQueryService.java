package com.jglearning.jg4seasons.listening;

import com.jglearning.jg4seasons.listening.api.LessonProgressQuery;
import com.jglearning.jg4seasons.listening.api.LessonProgressSummary;
import com.jglearning.jg4seasons.listening.persistence.ProgressStore;
import java.util.Collection;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class LessonProgressQueryService implements LessonProgressQuery {

    private final ProgressStore store;

    LessonProgressQueryService(ProgressStore store) {
        this.store = store;
    }

    @Override
    public Map<Long, Map<Long, LessonProgressSummary>> progressOf(
            Collection<Long> studentIds, Collection<Long> lessonIds) {
        return store.lessonProgressOf(studentIds, lessonIds);
    }
}
