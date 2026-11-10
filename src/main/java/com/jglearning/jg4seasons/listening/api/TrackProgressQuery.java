package com.jglearning.jg4seasons.listening.api;

import com.jglearning.jg4seasons.auth.api.AcademyStudents;
import com.jglearning.jg4seasons.content.api.LessonContent;
import java.util.Collection;
import java.util.Map;

/**
 * 교사 화면(4.3·4.4)용 학생 × 지문 트랙 집계 읽기. 다른 모듈(teacher)이 쓴다. track_progress에는 학원 칸이 없어서 학생 범위는 auth가 확인한 묶음
 * (AcademyStudents)으로만 받는다.
 */
public interface TrackProgressQuery {

    /** 학생 id → 트랙 id → 집계. 집계 행이 있는 것만(없으면 안 들음). 묶음이나 trackIds가 비면 빈 맵 */
    Map<Long, Map<Long, TrackProgressSummary>> trackProgressOf(AcademyStudents students, Collection<Long> trackIds);

    /** 회차 완료 규칙에서 이 목소리(us_w 등)가 필수인가 — 학생 회차 상세(2.3)의 required와 같은 판정 */
    boolean isRequired(LessonContent.Rule rule, String voice);
}
