package com.jglearning.jg4seasons.content.api;

import java.util.List;

/** 시험지 문항 읽기(단어시험 사진 채점). section은 "A"~"D" 문자열 — content는 wordtest에 의존하지 않는다 */
public interface WordTestItems {

    /** 그 회차·영역의 문항, 번호 순. 없으면 빈 목록 */
    List<WordTestItem> of(long lessonId, String section);

    /** 그 회차·영역의 문항 수(제출의 total) */
    int count(long lessonId, String section);
}
