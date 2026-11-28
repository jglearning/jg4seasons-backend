package com.jglearning.jg4seasons.talk.api;

import com.jglearning.jg4seasons.auth.api.AcademyStudents;
import java.util.Set;

/**
 * 프리토킹 학생 허용(ADR 0040). 다른 모듈(system)이 쓴다. 학원 스위치(features.talk)와 AND — 여기 값만 켜져도 학원이 꺼져 있으면 못 쓴다. 행이 없으면 꺼짐
 */
public interface TalkStudentAccess {

    /** 그 학원에서 켜진 학생 id, id 순 */
    Set<Long> enabledStudents(long academyId);

    /**
     * 묶음의 학생을 모두 켜거나 끄고(같은 값이면 그대로) 감사 기록 TALK_ACCESS_SET 한 건(학원 대상, detail은 학생 수·enabled만). 트랜잭션 안에서 부른다. 빈
     * 묶음은 IllegalArgumentException
     */
    void set(AcademyStudents students, boolean enabled, long actorId);
}
