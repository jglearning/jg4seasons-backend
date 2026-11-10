package com.jglearning.jg4seasons.clazz.api;

import java.time.LocalDate;
import java.util.List;

/**
 * 교사 진도표(4.5~4.7, ADR 0030). 다른 모듈(teacher)이 쓴다. 반은 학원 범위로 찾는다(다른 학원·없는 반 404). 교사 담당 여부는 호출자가 먼저 확인한다 —
 * teacher에서는 범위 확인을 거친 반만 받는 TeacherScope만 부른다.
 */
public interface ClassSchedules {

    /** 진도표 전체, seq 순(휴강 행 포함) */
    List<ScheduleEntry> schedule(long academyId, long classId);

    /**
     * 한 행의 날짜·메모 변경(4.6). today = 학원 날짜. 없는 seq 404, 지난 행 날짜 변경 409 SESSION_PAST, 순서 위반·휴강 행 날짜 변경 400. 같은 반 편집은 반 행
     * 잠금으로 줄 세운다. 바뀐 뒤 진도표 전체(seq 다시 매김)
     */
    List<ScheduleEntry> reschedule(long academyId, long classId, int seq, ScheduleChange change, LocalDate today);

    /**
     * 휴강(4.7). shiftFollowing이면 같은 회차를 새 행으로 다음 수업일에. 이미 휴강한 행이면 그대로. 없는 seq 404, 지난 행 409 SESSION_PAST. 바뀐 뒤 진도표
     * 전체
     */
    List<ScheduleEntry> cancel(
            long academyId, long classId, int seq, String note, boolean shiftFollowing, LocalDate today);
}
