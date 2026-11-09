package com.jglearning.jg4seasons.listening;

import com.jglearning.jg4seasons.listening.api.ProgressSummaries;
import com.jglearning.jg4seasons.listening.persistence.ProgressSummaryStore;
import java.time.Instant;
import java.util.Collection;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class ProgressSummaryService implements ProgressSummaries {

    private final ProgressSummaryStore store;

    ProgressSummaryService(ProgressSummaryStore store) {
        this.store = store;
    }

    @Override
    public int completedCount(long lessonId, Collection<Long> studentIds) {
        return store.completedCount(lessonId, studentIds);
    }

    @Override
    public int playedBetween(Collection<Long> studentIds, Instant from, Instant to) {
        return store.playedBetween(studentIds, from, to);
    }
}
