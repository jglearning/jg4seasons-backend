package com.jglearning.jg4seasons.content.api;

import java.util.List;

/** 시험지 문항 읽기(단어시험 사진 채점). section은 "A"~"D" 문자열 — content는 wordtest에 의존하지 않는다 */
public interface WordTestItems {

    /** 그 회차·영역의 문항, 번호 순. 없으면 빈 목록 */
    List<WordTestItem> of(long lessonId, String section);

    /** of와 같되 문항·허용 답 행을 공유 잠금(FOR SHARE)으로 — 채점 저장과 import의 정답 변경이 겹치지 않게. 트랜잭션 안에서만(밖이면 예외) */
    List<WordTestItem> ofLocked(long lessonId, String section);

    /** 그 회차·영역의 문항 수(제출의 total) */
    int count(long lessonId, String section);
}
