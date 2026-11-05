package com.jglearning.jg4seasons.clazz.api;

import java.util.List;
import java.util.Set;

/**
 * 교사의 담당 반(class_teacher). 다른 모듈(admin)이 쓴다. 교사가 그 학원 사용자인지는 호출자가 확인한다(사용자는 auth 모듈 소유). 바꾸는 메서드
 * (replace·replaceTeachersOf)는 호출자가 먼저 학원 잠금(academy.api.AcademyDirectory.lockForAdminWrite)을 잡은 트랜잭션에서 부른다 — 두 경로가
 * 교사 행·반 행·class_teacher(FK로 user 행)를 다른 순서로 잠그기 때문
 */
public interface ClassTeachers {

    /** 그 학원 반 중 이 교사가 맡은 반 id(오름차순) */
    List<Long> classIdsOf(long academyId, long teacherId);

    /**
     * 그 학원 안에서 이 교사의 담당 반을 classIds로 바꾼다. 하나라도 그 학원 반이 아니면 404 NOT_FOUND(아무것도 바꾸지 않음). 다른 학원의 배정은 건드리지
     * 않는다
     */
    void replace(long academyId, long teacherId, Set<Long> classIds);

    /** 이 반을 맡은 교사 id(오름차순). 다른 학원·없는 반은 404 NOT_FOUND */
    List<Long> teacherIdsOf(long academyId, long classId);

    /**
     * 이 반의 담당 교사를 teacherIds로 바꾼다. 반 행을 잠그고(같은 반 동시 변경을 줄 세움) 바뀐 것만 반영한다(빠진 교사만 지우고 새 교사만 넣음 — 범위
     * delete 없음). 다른 학원·없는 반은 404 NOT_FOUND
     */
    void replaceTeachersOf(long academyId, long classId, Set<Long> teacherIds);
}
