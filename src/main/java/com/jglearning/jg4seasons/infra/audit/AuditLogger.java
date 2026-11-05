package com.jglearning.jg4seasons.infra.audit;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * 감사 로그(audit_log) 기록기: 개인 코드·가입 코드 재발급, 학생 이동·퇴원. 호출한 작업과 같은 트랜잭션에서만 쓴다(MANDATORY) — 작업이 롤백되면 감사 행도
 * 남지 않고, 감사 저장이 실패하면 작업도 롤백된다. detail에 개인 코드·가입 코드 같은 비밀 값을 넣지 않는다.
 */
@Component
public class AuditLogger {

    private final AuditLogRepository logs;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    AuditLogger(AuditLogRepository logs, JsonMapper jsonMapper, Clock clock) {
        this.logs = logs;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(
            long academyId,
            long actorId,
            AuditAction action,
            AuditTarget target,
            long targetId,
            @Nullable Map<String, Object> detail) {
        String json = detail == null ? null : jsonMapper.writeValueAsString(detail);
        // DATETIME은 초 단위 반올림 — 내림해 저장해 실제보다 늦은 시각이 되지 않게
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        logs.save(new AuditLog(academyId, actorId, action, target.value(), targetId, json, now));
    }
}
