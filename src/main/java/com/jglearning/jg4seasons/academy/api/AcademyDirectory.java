package com.jglearning.jg4seasons.academy.api;

import java.time.ZoneId;
import java.util.Optional;

/** 다른 모듈이 쓰는 학원 조회. */
public interface AcademyDirectory {

    /** 학원 이름. 학원은 사용자·반의 FK라 반드시 있다 — 없으면 IllegalStateException */
    String nameOf(long academyId);

    /** 교재 사용권 적용 여부(false = 직영, 모든 권 무제한 — ADR 0031). 없는 학원은 데이터 오류라 IllegalStateException */
    boolean licenseEnforced(long academyId);

    /** 학원이 있고 정지(suspended)가 아니다. 학생 입장·세션 갱신 전에 확인한다 */
    boolean isActive(long academyId);

    /**
     * 학원 타임존(진도표·회차 열림의 "오늘"). settings가 없거나 timezone 키가 없으면 Asia/Seoul. 없는 학원·잘못된 ZoneId는 데이터 오류라
     * IllegalStateException
     */
    ZoneId zoneOf(long academyId);

    /**
     * 관리 쓰기(교사 계정·담당 반·반 생성·변경·가입 코드 재발급)를 학원 단위로 줄 세운다: 학원 행을 FOR UPDATE로 잠근다. 호출한 트랜잭션 안에서만
     * 쓰고(MANDATORY), 그 트랜잭션의 첫 읽기보다 먼저 부른다 — REPEATABLE READ 스냅샷이 잠금을 얻은 뒤에 잡혀야 앞선 관리 쓰기의 결과를 본다. 담당
     * 교사 배정은 교사 행·반 행·class_teacher(FK로 user 행)를 경로마다 다른 순서로 잠그므로, 이 잠금 하나로 교착을 막는다. 학원이 없으면 아무것도
     * 하지 않는다(이어지는 활성 관리자 확인이 401로 막는다)
     */
    void lockForAdminWrite(long academyId);

    /** 상태와 무관하게 찾는다(개발 시드용) */
    Optional<Long> findIdBySlug(String slug);
}
