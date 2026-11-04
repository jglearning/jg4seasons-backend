package com.jglearning.jg4seasons.content.api;

/** 입장 화면용 책·레벨 표시 정보. season은 "겨울"·"봄"·"여름"·"가을". */
public record BookSummary(String bookCode, String season, String levelCode, String levelNameKo, String levelColorHex) {}
