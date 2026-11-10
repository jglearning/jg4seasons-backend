package com.jglearning.jg4seasons.words.api;

import com.jglearning.jg4seasons.auth.api.AcademyStudents;
import java.util.Collection;
import java.util.Map;

/** 다른 모듈이 쓰는 회차 단어 학습 결과 조회. */
public interface WordStudies {

    /** 암기 모드 "알아요" 최고 수. 기록이 없으면 0. 학생이 그 학원 반 소속인지는 호출자가 먼저 확인한다(word_study에는 academy_id가 없다) */
    int memKnown(long studentId, long lessonId);

    /**
     * 교사 화면(4.3)용: 학생 id → 회차 id → 결과. 기록 있는 것만. word_study에는 학원 칸이 없어 학생 범위는 auth가 확인한 묶음으로만 받는다. 묶음이나
     * lessonIds가 비면 빈 맵
     */
    Map<Long, Map<Long, WordStudyScores>> scoresOf(AcademyStudents students, Collection<Long> lessonIds);
}
