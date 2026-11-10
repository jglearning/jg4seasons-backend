package com.jglearning.jg4seasons.clazz.api;

import java.util.List;

/**
 * 교사 진도표(4.5~4.7, ADR 0030). 다른 모듈(teacher)이 쓴다. 반은 학원 범위로 찾는다(다른 학원·없는 반 404). 교사 담당 여부는 호출자가 먼저 확인한다 —
 * teacher에서는 범위 확인을 거친 반만 받는 TeacherScope만 부른다.
 */
public interface ClassSchedules {

    /** 진도표 전체, seq 순(휴강 행 포함) */
    List<ScheduleEntry> schedule(long academyId, long classId);
}
