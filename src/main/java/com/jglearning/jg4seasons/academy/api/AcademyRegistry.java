package com.jglearning.jg4seasons.academy.api;

import java.util.List;

/** 학원 만들기·목록(운영자 — SYS-07, ADR 0031·0032). 다른 모듈(system)이 쓴다. 운영자 확인은 호출자가 먼저 한다. */
public interface AcademyRegistry {

    /** 학원 주소(slug): 소문자·숫자·- 2~30자, 영숫자로 시작. 요청 DTO의 @Pattern에도 쓴다 */
    String SLUG_REGEXP = "^[a-z0-9][a-z0-9-]{1,29}$";

    String SLUG_MESSAGE = "학원 주소는 소문자·숫자·- 2~30자, 영숫자로 시작해요";

    /** 시험 데이터(trial)의 데모 학원 자리라 운영 학원에 쓰지 않는다 */
    String RESERVED_SLUG = "demo";

    /**
     * 학원(active)을 만든다. slug가 있으면 409 ACADEMY_SLUG_TAKEN(동시 요청 포함), demo면 400 VALIDATION(slug). 호출한 트랜잭션 안에서 —
     * 뒤이은 관리자 만들기가 실패하면 학원도 남지 않는다
     */
    AcademySummary create(NewAcademy academy);

    /** 모든 학원, id 순 */
    List<AcademySummary> list();

    /** 없으면 404 NOT_FOUND */
    AcademySummary find(long academyId);
}
