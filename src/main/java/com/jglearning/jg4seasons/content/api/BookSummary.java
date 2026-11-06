package com.jglearning.jg4seasons.content.api;

/** 책·레벨 표시 정보. season은 "겨울"·"봄"·"여름"·"가을", label은 "Book {시즌 번호}"(L2-B1 → "Book 1"). */
public record BookSummary(
        String bookCode, String season, String label, String levelCode, String levelNameKo, String levelColorHex) {}
