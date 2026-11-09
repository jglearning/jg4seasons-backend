package com.jglearning.jg4seasons.clazz.api;

import java.time.LocalDate;
import java.util.List;

/**
 * 교사 화면(4.1·4.2)이 보는 반(TCH-01·02). 다른 모듈(teacher)이 쓴다. academyId·teacherId는 인증 주체(JWT)에서, wholeAcademy는 호출자가 DB의 지금
 * 역할로 정한다(관리자 = 학원 전체, 교사 = 담당 반만 — clazz는 auth를 보지 않는다). today는 학원 타임존 날짜.
 */
public interface TeacherClasses {

    /** 진행 중(active) 반, id 순. wholeAcademy면 학원 전체, 아니면 teacherId가 담당한 반 */
    List<TeacherClass> activeClasses(long academyId, long teacherId, boolean wholeAcademy, LocalDate today);

    /** 반 하나(상태 무관). 다른 학원·없는 반, wholeAcademy가 아닌데 담당이 아닌 반은 404 NOT_FOUND */
    TeacherClass classOf(long academyId, long teacherId, boolean wholeAcademy, long classId, LocalDate today);
}
