package com.jglearning.jg4seasons.talk;

import com.jglearning.jg4seasons.auth.api.AcademyStudents;
import com.jglearning.jg4seasons.infra.audit.AuditAction;
import com.jglearning.jg4seasons.infra.audit.AuditLogger;
import com.jglearning.jg4seasons.infra.audit.AuditTarget;
import com.jglearning.jg4seasons.talk.api.TalkStudentAccess;
import com.jglearning.jg4seasons.talk.persistence.TalkStudentAccessStore;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** {@link TalkStudentAccess} 구현. 학원 범위는 받은 묶음(auth가 확인한 학생)의 학원 */
@Service
class TalkStudentAccessService implements TalkStudentAccess {

    private final TalkStudentAccessStore store;
    private final AuditLogger auditLogger;

    TalkStudentAccessService(TalkStudentAccessStore store, AuditLogger auditLogger) {
        this.store = store;
        this.auditLogger = auditLogger;
    }

    @Override
    public Set<Long> enabledStudents(long academyId) {
        return store.enabledStudents(academyId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void set(AcademyStudents students, boolean enabled, long actorId) {
        if (students.isEmpty()) {
            throw new IllegalArgumentException("켜고 끌 학생이 없다");
        }
        long academyId = students.academyId();
        store.upsert(academyId, students.ids(), enabled, actorId);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("students", students.ids().size());
        detail.put("enabled", enabled);
        auditLogger.record(academyId, actorId, AuditAction.TALK_ACCESS_SET, AuditTarget.ACADEMY, academyId, detail);
    }
}
