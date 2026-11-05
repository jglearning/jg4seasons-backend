package com.jglearning.jg4seasons.clazz.api;

import com.jglearning.jg4seasons.content.api.BookLessons;
import java.time.LocalDate;
import java.util.List;

/**
 * 새 반. name은 받은 그대로(앞뒤 공백은 만들 때 뺀다), weekdays는 1(월)~7(일). 권과 그 권의 1~24회차는 content.api.BookDirectory.findLessons의
 * 결과 하나로 받는다 — 권 id와 다른 권의 회차가 섞이지 않게.
 */
public record NewClass(String name, BookLessons lessons, LocalDate startDate, List<Integer> weekdays) {}
