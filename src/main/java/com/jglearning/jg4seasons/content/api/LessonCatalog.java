package com.jglearning.jg4seasons.content.api;

import java.util.List;
import java.util.Optional;

/** 다른 모듈이 쓰는 회차 내용 조회. */
public interface LessonCatalog {

    /**
     * 회차 코드(L2-B1-05, 대소문자는 DB 정렬 규칙대로 구분하지 않음)로 내용을 찾는다. 없는 회차면 empty. 문장 시각이 있는데 그 지문 문장 수와 개수가 다르거나
     * variant 형식이 틀리면 데이터 오류라 IllegalStateException
     */
    Optional<LessonContent> findByCode(String lessonCode);

    /**
     * 지문 트랙(kind=passage) id로 트랙·회차·권·지문 번호·속도·길이를 찾는다. 없거나 지문 트랙이 아니면 empty. 트랙의 지문이 없거나 다른 회차 지문이거나
     * variant가 p{지문 no}:{속도}가 아니면 데이터 오류라 IllegalStateException
     */
    Optional<PassageTrack> findPassageTrack(long trackId);

    /** 권의 회차 전부, number 순. 없는 권이면 빈 목록 */
    List<LessonSummary> lessonsOfBook(long bookId);
}
