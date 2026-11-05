package com.jglearning.jg4seasons.clazz.api;

import java.time.LocalDate;
import java.util.List;

/**
 * 새 반. name은 받은 그대로(앞뒤 공백은 만들 때 뺀다), weekdays는 1(월)~7(일), lessonIdsByNumber는 그 권의 1~24회차 id(index 0 = 1회차,
 * content.api.BookDirectory.findLessons).
 */
public record NewClass(
        String name, long bookId, LocalDate startDate, List<Integer> weekdays, List<Long> lessonIdsByNumber) {}
