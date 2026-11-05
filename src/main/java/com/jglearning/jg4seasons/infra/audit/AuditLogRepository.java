package com.jglearning.jg4seasons.infra.audit;

import com.jglearning.jg4seasons.infra.persistence.AcademyScopedRepository;

/** 추가만(save). 조회는 관리자 화면이 생길 때. */
interface AuditLogRepository extends AcademyScopedRepository<AuditLog, Long> {

    AuditLog save(AuditLog log);
}
