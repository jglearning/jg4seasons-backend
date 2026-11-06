package com.jglearning.jg4seasons.words.api;

/** 다른 모듈이 쓰는 회차 단어 학습 결과 조회. */
public interface WordStudies {

    /** 암기 모드 "알아요" 최고 수. 기록이 없으면 0. 학생이 그 학원 반 소속인지는 호출자가 먼저 확인한다(word_study에는 academy_id가 없다) */
    int memKnown(long studentId, long lessonId);
}
