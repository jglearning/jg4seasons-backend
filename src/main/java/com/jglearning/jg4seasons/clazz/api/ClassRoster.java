package com.jglearning.jg4seasons.clazz.api;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 반 소속(enrollment) 쓰기와 명단 조회(ADM-02·05·06, 이슈 #29). 다른 모듈(admin·teacher)이 쓴다. academyId는 인증 주체(JWT)에서, 날짜(on·today)는
 * 학원 타임존 날짜로 넘긴다. 다른 학원·없는 반은 404 NOT_FOUND. 학생이 그 학원 학생인지는 호출자가 확인한다(사용자는 auth 소유). 쓰기는 호출자가 학원 잠금
 * (academy.api.AcademyDirectory.lockForAdminWrite)을 먼저 잡은 트랜잭션에서 부른다.
 */
public interface ClassRoster {

    /** 반의 현재 소속(left_at 없음) 학생 id, id 순 */
    List<Long> currentStudentIds(long academyId, long classId);

    /**
     * 학생들을 그 반에 넣는다(joined_at = on). 반이 active가 아니면 409 CLASS_NOT_ACTIVE, 그 권이 시작 전·기간 안이 아니면 403 LICENSE_INACTIVE, 그 권
     * 학생 수 한도를 넘으면 409 LICENSE_SEATS_FULL(ADR 0031 — 하나라도 넘으면 아무도 넣지 않는다)
     */
    void enroll(long academyId, long classId, List<Long> studentIds, LocalDate on);

    /** 학생마다 지금 반 하나(그 학원 반 중 id가 가장 작은 것). 소속이 없으면 맵에 없다 */
    Map<Long, ClassRef> currentClassOf(long academyId, Collection<Long> studentIds);

    /** 그 학원 반의 현재 소속을 모두 끝낸다(left_at = on, 퇴원). 끝낸 반 id(id 순) */
    List<Long> leaveAll(long academyId, long studentId, LocalDate on);

    /**
     * 이동: 대상 반 말고 지금 소속을 모두 끝내고(left_at = on), 대상 반에 없으면 넣는다(결정 H). 대상 반이 active가 아니면 409 CLASS_NOT_ACTIVE, 이미
     * 그 반에만 있으면 409 ALREADY_IN_CLASS. 대상 반 권의 사용권은 등록과 같다(403 LICENSE_INACTIVE·409 LICENSE_SEATS_FULL, 같은 권 반끼리는 자리를 더
     * 쓰지 않음)
     */
    MoveResult move(long academyId, long studentId, long toClassId, LocalDate on);

    /** 학생이 이 교사가 맡은 그 학원 반 중 하나에 지금 소속인가 */
    boolean isTaughtBy(long academyId, long studentId, long teacherId);

    /** 학생이 그 학원 반 중 하나에 지금 소속인가 */
    boolean isCurrentMemberOfAny(long academyId, long studentId);

    /** 진행 중(active) 반 전부, id 순. today는 학원 타임존 날짜 */
    List<ActiveClassToday> activeClassesToday(long academyId, LocalDate today);
}
