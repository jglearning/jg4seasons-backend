package com.jglearning.jg4seasons.listening.api;

import java.util.Collection;
import java.util.Map;

/**
 * 교사 화면(4.1·4.2)용 반 학생 × 회차 진도 읽기. 다른 모듈(teacher)이 쓴다. lesson_progress에는 학원 칸이 없어서 studentIds가 곧 학원 범위다 — 호출자는
 * clazz api가 학원 범위로 고른 학생 id만 넘긴다.
 */
public interface LessonProgressQuery {

    /** 학생 id → 회차 id → 진도. 진도 행이 있는 것만 담는다(없으면 시작 전). 둘 중 하나라도 빈 목록이면 빈 맵 */
    Map<Long, Map<Long, LessonProgressSummary>> progressOf(Collection<Long> studentIds, Collection<Long> lessonIds);
}
