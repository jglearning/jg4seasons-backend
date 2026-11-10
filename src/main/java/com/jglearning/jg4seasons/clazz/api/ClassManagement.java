package com.jglearning.jg4seasons.clazz.api;

import java.util.List;
import java.util.Optional;

/**
 * 관리자 반 관리(ADM-01·ADM-03). 다른 모듈(admin)이 쓴다. academyId는 인증 주체(JWT)에서 온 값이어야 한다. 다른 학원·없는 반은 404
 * NOT_FOUND.
 */
public interface ClassManagement {

    /** 반 이름 최대 길이(앞뒤 공백 뺀 뒤) */
    int NAME_MAX = 50;

    /**
     * 반을 만들고 개강일(포함)부터 수업 요일에 맞는 진도표(class_session, scheduled — 권의 회차 수만큼: 블랙 28, 그 밖 24회)와 가입 코드를 함께 만든다. 이름이 비었거나 50자 초과면 400
     * VALIDATION(name), 요일이 잘못되면 400 VALIDATION(weekdays), 그 권이 시작 전·기간 안이 아니면 403 LICENSE_INACTIVE(ADR 0031) — 모두 아무것도
     * 만들지 않는다
     */
    ClassDetail create(long academyId, NewClass newClass);

    /** id 순. status가 비면 전부 */
    List<ClassDetail> list(long academyId, Optional<ClassStatus> status);

    ClassDetail find(long academyId, long classId);

    /** 반 행을 잠그고 바꾼다. 빈 칸은 그대로. active가 아니면 그 반 가입 코드로 입장할 수 없다 */
    ClassDetail change(long academyId, long classId, ClassChange change);

    /** 새 가입 코드와 발급 시각. 이미 입장한 학생의 로그인은 그대로 둔다(옛 코드로 새 입장만 막힘) */
    ClassDetail regenerateAccessCode(long academyId, long classId);
}
