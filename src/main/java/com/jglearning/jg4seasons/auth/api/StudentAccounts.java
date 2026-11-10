package com.jglearning.jg4seasons.auth.api;

import java.util.Collection;
import java.util.List;

/**
 * 학생 계정과 개인 코드(ADM-02·AUTH-05·ADM-06, ADR-22). 다른 모듈(admin·teacher)이 쓴다. academyId는 인증 주체(JWT)에서 온 값이어야 한다.
 *
 * <p>개인 코드는 숫자 4자리, 학원 안 고유, DB에는 HMAC만 — 평문은 IssuedCode로 한 번만 돌려준다. 다른 학원 학생·교사 id·없는 id는 404
 * NOT_FOUND(아무것도 바꾸지 않음). 이름은 앞뒤 공백을 지운 뒤 1~30자(아니면 400 VALIDATION name). 반 소속(enrollment)은 다루지 않는다(clazz).
 * 같은 학원의 코드 발급이 겹치지 않게 호출자는 학원 잠금(academy.api.AcademyDirectory.lockForAdminWrite)을 먼저 잡는다.
 */
public interface StudentAccounts {

    /** 이름 검색 결과 최대 수(결정 E) */
    int SEARCH_LIMIT = 50;

    /** 학생을 만들고 개인 코드를 발급한다. 요청 순서대로 */
    List<IssuedCode> create(long academyId, List<NewStudent> students);

    /**
     * 새 개인 코드(studentIds 순서대로, 같은 id는 첫 순서로 한 번). 옛 코드는 바로 무효, 그 학생들의 refresh는 모두 끊긴다. 코드가 없던 학생은 새로 만든다. 하나라도
     * 그 학원 학생이 아니면 404
     */
    List<IssuedCode> regenerate(long academyId, List<Long> studentIds);

    StudentAccount find(long academyId, long studentId);

    /** 그 학원 학생만, 이름 순(같으면 id 순). 학원 밖·없는 id는 빠진다 */
    List<StudentAccount> findAll(long academyId, Collection<Long> studentIds);

    /**
     * findAll과 같은 학생(그 학원 학생만, 이름 순)을 명단으로: 계정 + 집계 조회에 넘길 범위 묶음. 진도·단어 집계를 읽는 다른 모듈은 이 묶음만 받는다(AcademyStudents)
     */
    StudentRoster roster(long academyId, Collection<Long> candidateIds);

    /** inactive로 바뀌면 그 학생의 refresh를 모두 끊는다(코드는 그대로 — 결정 B) */
    StudentChangeResult change(long academyId, long studentId, StudentChange change);

    /** 이름 부분 일치(앞뒤 공백 제거, %·_는 글자 그대로), 퇴원 포함, 이름 순, 최대 SEARCH_LIMIT명 */
    List<StudentAccount> searchByName(long academyId, String namePart);

    /** 그 학원 학생 id 전부(퇴원 포함) */
    List<Long> studentIds(long academyId);
}
