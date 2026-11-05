package com.jglearning.jg4seasons.content.api;

import java.util.Optional;

/** 다른 모듈이 쓰는 책 조회. */
public interface BookDirectory {

    /** 권마다 회차 수(1~24) */
    int LESSONS_PER_BOOK = 24;

    /** 책은 반의 FK라 반드시 있다 — 없으면 IllegalStateException */
    BookSummary summaryOf(long bookId);

    /**
     * 권 코드(예: L2-B1. 공백은 정리하지 않고, 대소문자는 DB 정렬 규칙대로 구분하지 않음)로 권과 회차 1~24의 id를 찾는다. 없는 권이면 empty. 회차 번호가 정확히 1~24가 아니면 기준 데이터 오류라
     * IllegalStateException
     */
    Optional<BookLessons> findLessons(String bookCode);
}
