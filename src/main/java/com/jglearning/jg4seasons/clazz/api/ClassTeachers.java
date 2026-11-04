package com.jglearning.jg4seasons.clazz.api;

import java.util.List;
import java.util.Set;

/** 교사의 담당 반(class_teacher). 다른 모듈(admin)이 쓴다. 교사가 그 학원 사용자인지는 호출자가 확인한다(사용자는 auth 모듈 소유). */
public interface ClassTeachers {

    /** 그 학원 반 중 이 교사가 맡은 반 id(오름차순) */
    List<Long> classIdsOf(long academyId, long teacherId);

    /**
     * 그 학원 안에서 이 교사의 담당 반을 classIds로 바꾼다. 하나라도 그 학원 반이 아니면 404 NOT_FOUND(아무것도 바꾸지 않음). 다른 학원의 배정은 건드리지
     * 않는다
     */
    void replace(long academyId, long teacherId, Set<Long> classIds);
}
