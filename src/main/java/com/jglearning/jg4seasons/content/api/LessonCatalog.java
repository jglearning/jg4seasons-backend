package com.jglearning.jg4seasons.content.api;

import java.util.Optional;

/** 다른 모듈이 쓰는 회차 내용 조회. */
public interface LessonCatalog {

    /**
     * 회차 코드(L2-B1-05, 대소문자는 DB 정렬 규칙대로 구분하지 않음)로 내용을 찾는다. 없는 회차면 empty. 문장 시각이 있는데 그 지문 문장 수와 개수가 다르거나
     * variant 형식이 틀리면 데이터 오류라 IllegalStateException
     */
    Optional<LessonContent> findByCode(String lessonCode);
}
