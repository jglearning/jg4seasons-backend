package com.jglearning.jg4seasons.words;

import com.jglearning.jg4seasons.words.api.WordStudies;
import com.jglearning.jg4seasons.words.persistence.WordStudyStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class WordStudiesService implements WordStudies {

    private final WordStudyStore store;

    WordStudiesService(WordStudyStore store) {
        this.store = store;
    }

    @Override
    public int memKnown(long studentId, long lessonId) {
        return store.memKnown(studentId, lessonId).orElse(0);
    }
}
