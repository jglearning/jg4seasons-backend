package com.jglearning.jg4seasons.content.api;

/** 다른 모듈이 쓰는 책 조회. */
public interface BookDirectory {

    /** 책은 반의 FK라 반드시 있다 — 없으면 IllegalStateException */
    BookSummary summaryOf(long bookId);
}
