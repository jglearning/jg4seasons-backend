package com.jglearning.jg4seasons.admin;

import com.jglearning.jg4seasons.academy.api.AcademyDirectory;
import com.jglearning.jg4seasons.admin.web.dto.PersonalCodeItem;
import com.jglearning.jg4seasons.admin.web.dto.PersonalCodesRegenerateRequest;
import com.jglearning.jg4seasons.admin.web.dto.PersonalCodesResponse;
import com.jglearning.jg4seasons.admin.web.dto.StudentCreateRequest;
import com.jglearning.jg4seasons.admin.web.dto.StudentCreatedResponse;
import com.jglearning.jg4seasons.admin.web.dto.StudentListResponse;
import com.jglearning.jg4seasons.admin.web.dto.StudentResponse;
import com.jglearning.jg4seasons.auth.api.IssuedCode;
import com.jglearning.jg4seasons.auth.api.NewStudent;
import com.jglearning.jg4seasons.auth.api.StaffAccounts;
import com.jglearning.jg4seasons.auth.api.StudentAccount;
import com.jglearning.jg4seasons.auth.api.StudentAccounts;
import com.jglearning.jg4seasons.clazz.api.ClassRoster;
import com.jglearning.jg4seasons.infra.audit.AuditAction;
import com.jglearning.jg4seasons.infra.audit.AuditLogger;
import com.jglearning.jg4seasons.infra.audit.AuditTarget;
import com.jglearning.jg4seasons.infra.web.error.ApiException;
import com.jglearning.jg4seasons.infra.web.error.CommonErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 5.2 관리자 학생 API(ADM-02·05·06·AUTH-05). 계정·개인 코드는 auth, 반 소속은 clazz, 감사는 infra.audit — 한 트랜잭션에서 조립한다(소속이 409면
 * 학생도 코드도 남지 않음). academyId·actorId는 컨트롤러가 인증 주체(JWT)에서 넘긴다. 모든 메서드는 먼저 요청한 사람이 지금도 활성 관리자인지 확인하고, 쓰기는
 * 그보다 먼저 학원 잠금으로 같은 학원 관리 쓰기를 줄 세운다 — 개인 코드 발급의 "겹치는지 확인 → 저장"이 동시에 돌지 않게. 날짜(가입·퇴원·이동)는 학원 타임존.
 * 개인 코드 평문은 응답에만 싣고 감사 detail·로그에 남기지 않는다.
 */
@Service
@Transactional
public class StudentAdminService {

    private final AcademyDirectory academies;
    private final StaffAccounts staff;
    private final StudentAccounts students;
    private final ClassRoster roster;
    private final AuditLogger auditLogger;
    private final Clock clock;

    StudentAdminService(
            AcademyDirectory academies,
            StaffAccounts staff,
            StudentAccounts students,
            ClassRoster roster,
            AuditLogger auditLogger,
            Clock clock) {
        this.academies = academies;
        this.staff = staff;
        this.students = students;
        this.roster = roster;
        this.auditLogger = auditLogger;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public StudentListResponse list(long academyId, long actorId, long classId) {
        staff.requireActiveAdmin(academyId, actorId);
        List<StudentResponse> items = students.findAll(academyId, roster.currentStudentIds(academyId, classId)).stream()
                .map(StudentAdminService::toResponse)
                .toList();
        return new StudentListResponse(items, items.size());
    }

    /** 반이 없으면 404, active가 아니면 409 CLASS_NOT_ACTIVE — 둘 다 아무도 만들지 않는다(트랜잭션 롤백) */
    public StudentCreatedResponse create(long academyId, long actorId, long classId, StudentCreateRequest request) {
        academies.lockForAdminWrite(academyId);
        staff.requireActiveAdmin(academyId, actorId);
        roster.currentStudentIds(academyId, classId); // 다른 학원 반이면 학생을 만들기 전에 404
        List<IssuedCode> issued = students.create(
                academyId,
                request.students().stream()
                        .map(s -> new NewStudent(s.name(), s.school(), s.grade()))
                        .toList());
        roster.enroll(
                academyId, classId, issued.stream().map(IssuedCode::studentId).toList(), today(academyId));
        return new StudentCreatedResponse(
                issued.stream().map(StudentAdminService::toItem).toList());
    }

    /** 그 반 현재 소속 학생만(결정 J). 하나라도 아니면 404, 아무것도 바꾸지 않는다. 같은 id는 한 번(첫 순서) */
    public PersonalCodesResponse regenerate(
            long academyId, long actorId, long classId, PersonalCodesRegenerateRequest request) {
        academies.lockForAdminWrite(academyId);
        staff.requireActiveAdmin(academyId, actorId);
        List<Long> ids = List.copyOf(new LinkedHashSet<>(request.studentIds()));
        if (!new HashSet<>(roster.currentStudentIds(academyId, classId)).containsAll(ids)) {
            throw new ApiException(CommonErrorCode.NOT_FOUND, "그 반 학생이 아니에요");
        }
        List<IssuedCode> issued = students.regenerate(academyId, ids);
        for (IssuedCode code : issued) {
            auditLogger.record(
                    academyId,
                    actorId,
                    AuditAction.PERSONAL_CODE_REGENERATE,
                    AuditTarget.STUDENT,
                    code.studentId(),
                    Map.of("classId", classId));
        }
        return new PersonalCodesResponse(
                issued.stream().map(StudentAdminService::toItem).toList());
    }

    private LocalDate today(long academyId) {
        return LocalDate.now(clock.withZone(academies.zoneOf(academyId)));
    }

    private static PersonalCodeItem toItem(IssuedCode code) {
        return new PersonalCodeItem(code.studentId(), code.name(), code.personalCode());
    }

    static StudentResponse toResponse(StudentAccount account) {
        return new StudentResponse(
                account.id(),
                account.name(),
                account.school(),
                account.grade(),
                account.status().value(),
                account.codeIssuedAt(),
                account.lastLoginAt());
    }
}
