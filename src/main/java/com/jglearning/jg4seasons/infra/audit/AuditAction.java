package com.jglearning.jg4seasons.infra.audit;

/** 감사 로그 대상 행위(audit_log.action, 상수 이름 그대로 저장). */
public enum AuditAction {
    /** 개인 코드 재발급 */
    PERSONAL_CODE_REGENERATE,
    /** 반 가입 코드 재발급 */
    CODE_REGENERATE,
    /** 학생 반 이동 */
    STUDENT_MOVE,
    /** 학생 퇴원(비활성화) */
    STUDENT_DEACTIVATE
}
