package com.jglearning.jg4seasons.wordtest.grading;

/** 시험지 영역. 영역마다 쪽이 달라 사진 한 장 = 영역 하나. DB 값은 이름 그대로(V12 CHECK) */
public enum Section {
    /** 뜻 쓰기(영어 → 우리말) */
    A,
    /** 철자 쓰기(우리말 → 영어) */
    B,
    /** 짧은 글 빈칸 */
    C,
    /** 파생어 쓰기 */
    D
}
