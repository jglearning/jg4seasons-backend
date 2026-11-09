package com.jglearning.jg4seasons.teacher;

import com.jglearning.jg4seasons.academy.api.AcademyDirectory;
import com.jglearning.jg4seasons.auth.api.IssuedCode;
import com.jglearning.jg4seasons.auth.api.StaffAccounts;
import com.jglearning.jg4seasons.auth.api.StaffRole;
import com.jglearning.jg4seasons.auth.api.StudentAccounts;
import com.jglearning.jg4seasons.clazz.api.ClassRoster;
import com.jglearning.jg4seasons.infra.audit.AuditAction;
import com.jglearning.jg4seasons.infra.audit.AuditLogger;
import com.jglearning.jg4seasons.infra.audit.AuditTarget;
import com.jglearning.jg4seasons.infra.web.error.ApiException;
import com.jglearning.jg4seasons.infra.web.error.CommonErrorCode;
import com.jglearning.jg4seasons.teacher.web.dto.PersonalCodeResponse;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 4.8 교사의 학생 개인 코드 재발급(T-04). 교사는 담당 반에 지금 소속인 학생만, 관리자는 학원 안 어느 반이든 지금 소속인 학생(결정 K) — 그 밖(다른 학원·
 * 담당 아님·퇴원·반 없음·학생 아님)은 404. academyId·actorId는 컨트롤러가 인증 주체(JWT)에서 넘긴다. 학원 잠금 → 활성 교사·관리자 확인 순서는 관리자
 * API와 같다(같은 학원 코드 발급을 줄 세움). 평문은 응답에만, 감사 detail·로그에 남기지 않는다.
 */
@Service
@Transactional
public class TeacherStudentService {

    private final AcademyDirectory academies;
    private final StaffAccounts staff;
    private final StudentAccounts students;
    private final ClassRoster roster;
    private final AuditLogger auditLogger;

    TeacherStudentService(
            AcademyDirectory academies,
            StaffAccounts staff,
            StudentAccounts students,
            ClassRoster roster,
            AuditLogger auditLogger) {
        this.academies = academies;
        this.staff = staff;
        this.students = students;
        this.roster = roster;
        this.auditLogger = auditLogger;
    }

    public PersonalCodeResponse regeneratePersonalCode(long academyId, long actorId, long studentId) {
        academies.lockForAdminWrite(academyId);
        StaffRole role = staff.requireActiveStaff(academyId, actorId);
        boolean allowed = switch (role) {
            case TEACHER -> roster.isTaughtBy(academyId, studentId, actorId);
            case ADMIN -> roster.isCurrentMemberOfAny(academyId, studentId);
        };
        if (!allowed) {
            throw new ApiException(CommonErrorCode.NOT_FOUND, "담당 반 학생이 아니에요");
        }
        IssuedCode issued = students.regenerate(academyId, List.of(studentId)).get(0);
        auditLogger.record(
                academyId,
                actorId,
                AuditAction.PERSONAL_CODE_REGENERATE,
                AuditTarget.STUDENT,
                studentId,
                Map.of("via", "teacher"));
        return new PersonalCodeResponse(issued.studentId(), issued.name(), issued.personalCode());
    }
}
