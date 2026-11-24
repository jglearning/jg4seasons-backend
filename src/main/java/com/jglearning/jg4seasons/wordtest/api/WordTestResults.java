package com.jglearning.jg4seasons.wordtest.api;

import com.jglearning.jg4seasons.auth.api.AcademyStudents;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 교사 조회용 단어시험 결과(D-5·D-6). 제출·답안 표에 학원 칸이 없어 학원 경계는 학생 묶음(AcademyStudents)만 — 묶음 밖 학생의 제출은 없는 것과 같다. 담당 반 범위는
 * 부르는 쪽(teacher)이 묶음을 만들 때 정한다. 교사에게는 unclear 문항에도 정답을 준다(사진을 보고 판단)
 */
public interface WordTestResults {

    /** 학원이 비활성이면 401, 기능이 꺼져 있으면 404 FEATURE_DISABLED(ApiException) */
    void requireEnabled(long academyId);

    /** 그 회차·영역에 시험지(문항)가 있는지. section은 A~D(대문자) */
    boolean hasItems(long lessonId, String section);

    /** 학생별 그 회차·영역의 마지막 시도. 제출이 없는 학생은 키가 없다 */
    Map<Long, Summary> latestOf(AcademyStudents students, long lessonId, String section);

    /** 묶음 안 학생의 제출만. 밖이거나 없으면 empty */
    Optional<Detail> detailOf(AcademyStudents students, long submissionId);

    /**
     * @param status pending·grading·graded·failed(2단계 confirming·rejected)
     * @param score graded가 아니면 null
     */
    record Summary(long id, String status, Integer score, int total, int attemptNo, Instant submittedAt) {}

    /**
     * @param items 번호 순, graded가 아니면 null
     * @param imageUrl 짧은 서명 주소(5분), 사진 키가 없거나 저장소가 주소를 주지 않으면(로컬) null. 서명 실패는 예외(500)
     */
    record Detail(
            long id,
            long studentId,
            String lessonCode,
            String section,
            int attemptNo,
            String status,
            Integer score,
            int total,
            Instant submittedAt,
            Instant gradedAt,
            List<Item> items,
            String imageUrl) {}

    /**
     * @param written 읽은 글자, 빈칸·읽을 수 없음이면 ""
     * @param result correct·wrong·blank·unclear
     * @param answer 정답(unclear에도)
     */
    record Item(int no, String prompt, String written, String result, String answer) {}
}
