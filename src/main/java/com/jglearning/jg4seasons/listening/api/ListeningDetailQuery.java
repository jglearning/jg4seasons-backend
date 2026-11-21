package com.jglearning.jg4seasons.listening.api;

import com.jglearning.jg4seasons.auth.api.AcademyStudents;
import com.jglearning.jg4seasons.content.api.LessonContent;
import java.util.Collection;

/**
 * 교사 화면(4.3·4.4)용 학생 × 회차의 지문 트랙 집계와 지문 요약 읽기. 다른 모듈(teacher)이 쓴다. track_progress·passage_progress에는 학원 칸이 없어서 학생
 * 범위는 auth가 확인한 묶음(AcademyStudents)으로만 받는다.
 *
 * <p>지문 요약은 학생 회차 상세(2.3)·청취 전송 응답과 같은 계산을 거친다 — 화면마다 지문 완료가 달라지지 않는다.
 */
public interface ListeningDetailQuery {

    /** 묶음의 학생 × 넘긴 회차들. 회차가 몇 개든 집계 표마다 쿼리 하나. 묶음이나 회차가 비면 둘 다 빈 맵 */
    ListeningDetail detailOf(AcademyStudents students, Collection<LessonContent> lessons);
}
