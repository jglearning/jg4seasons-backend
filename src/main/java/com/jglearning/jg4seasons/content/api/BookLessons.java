package com.jglearning.jg4seasons.content.api;

import java.util.List;

/** 권과 그 권의 회차 id. lessonIdsByNumber의 index 0 = 1회차. */
public record BookLessons(long bookId, List<Long> lessonIdsByNumber) {

    public BookLessons {
        lessonIdsByNumber = List.copyOf(lessonIdsByNumber);
    }
}
