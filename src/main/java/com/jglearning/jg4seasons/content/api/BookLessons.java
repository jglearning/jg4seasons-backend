package com.jglearning.jg4seasons.content.api;

import java.util.List;

/** 권과 그 권의 회차 id. lessonIdsByNumber의 index 0 = 진도표 1행 회차, 크기 = 권의 회차 수(진도표 행 수). */
public record BookLessons(long bookId, List<Long> lessonIdsByNumber) {

    public BookLessons {
        lessonIdsByNumber = List.copyOf(lessonIdsByNumber);
    }
}
