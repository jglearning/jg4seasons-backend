package com.jglearning.jg4seasons.content;

import com.jglearning.jg4seasons.content.api.WordTestItem;
import com.jglearning.jg4seasons.content.api.WordTestItems;
import com.jglearning.jg4seasons.content.persistence.WordTestItemStore;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class WordTestItemsService implements WordTestItems {

    private final WordTestItemStore store;

    WordTestItemsService(WordTestItemStore store) {
        this.store = store;
    }

    @Override
    public List<WordTestItem> of(long lessonId, String section) {
        return store.items(lessonId, section);
    }

    @Override
    public int count(long lessonId, String section) {
        return store.count(lessonId, section);
    }
}
