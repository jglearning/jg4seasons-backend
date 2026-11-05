package com.jglearning.jg4seasons.content;

import java.util.List;
import org.springframework.data.repository.Repository;

interface LessonRepository extends Repository<Lesson, Long> {

    List<Lesson> findByBookIdOrderByNumber(Long bookId);
}
