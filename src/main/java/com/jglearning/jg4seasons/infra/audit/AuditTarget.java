package com.jglearning.jg4seasons.infra.audit;

import java.util.Locale;

/** 감사 로그 대상 종류(audit_log.target_type, 소문자로 저장). */
public enum AuditTarget {
    CLASS,
    STUDENT,
    ACADEMY;

    /** DB 값(소문자, 로캘 무관) */
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }
}
