package com.jglearning.jg4seasons.infra.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.MutableClock;
import com.jglearning.jg4seasons.TestData;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Transactional;

/** 감사 로그(audit_log): 추가만, 호출한 작업과 같은 트랜잭션. 시각은 주입 Clock(초 내림, UTC). */
@IntegrationTest
class AuditLoggerTest {

    @Autowired
    AuditLogger auditLogger;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MutableClock clock;

    TestData data;

    @BeforeEach
    void setUp() {
        data = new TestData(jdbc);
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    @Test
    @Transactional
    void 한_행을_추가한다() {
        long academyId = data.academy("감사 학원");
        long actorId = data.staff(academyId, "관리자", "admin");
        clock.set(Instant.parse("2026-11-05T03:04:05.987Z"));

        auditLogger.record(academyId, actorId, AuditAction.CODE_REGENERATE, AuditLogger.TARGET_CLASS, 77L, null);

        Map<String, Object> row = jdbc.queryForMap(
                "select academy_id, actor_id, action, target_type, target_id, detail,"
                        + " date_format(created_at, '%Y-%m-%d %H:%i:%s') as created"
                        + " from audit_log where academy_id = ?",
                academyId);
        assertThat(row)
                .containsEntry("actor_id", actorId)
                .containsEntry("action", "CODE_REGENERATE")
                .containsEntry("target_type", "class")
                .containsEntry("target_id", 77L)
                .containsEntry("detail", null)
                .containsEntry("created", "2026-11-05 03:04:05");
    }

    @Test
    @Transactional
    void detail은_JSON() {
        long academyId = data.academy("감사 학원");
        long actorId = data.staff(academyId, "관리자", "admin");

        auditLogger.record(
                academyId,
                actorId,
                AuditAction.STUDENT_MOVE,
                AuditLogger.TARGET_STUDENT,
                5L,
                Map.of("fromClassId", 3, "toClassId", 4));

        Integer toClassId = jdbc.queryForObject(
                "select json_extract(detail, '$.toClassId') from audit_log where academy_id = ?",
                Integer.class,
                academyId);
        assertThat(toClassId).isEqualTo(4);
    }

    @Test
    void 트랜잭션_없이_부르면_예외() {
        assertThatThrownBy(() ->
                        auditLogger.record(1L, 1L, AuditAction.CODE_REGENERATE, AuditLogger.TARGET_CLASS, 1L, null))
                .isInstanceOf(IllegalTransactionStateException.class);
    }
}
