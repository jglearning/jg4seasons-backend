package com.jglearning.jg4seasons.admin;

import com.jglearning.jg4seasons.academy.api.AcademyDirectory;
import com.jglearning.jg4seasons.admin.web.dto.PersonalCodeItem;
import com.jglearning.jg4seasons.admin.web.dto.PersonalCodesRegenerateRequest;
import com.jglearning.jg4seasons.admin.web.dto.PersonalCodesResponse;
import com.jglearning.jg4seasons.admin.web.dto.StudentCreateRequest;
import com.jglearning.jg4seasons.admin.web.dto.StudentCreatedResponse;
import com.jglearning.jg4seasons.admin.web.dto.StudentListResponse;
import com.jglearning.jg4seasons.admin.web.dto.StudentMoveRequest;
import com.jglearning.jg4seasons.admin.web.dto.StudentResponse;
import com.jglearning.jg4seasons.admin.web.dto.StudentSearchRequest;
import com.jglearning.jg4seasons.admin.web.dto.StudentSearchResponse;
import com.jglearning.jg4seasons.admin.web.dto.StudentSearchResponse.StudentSearchClass;
import com.jglearning.jg4seasons.admin.web.dto.StudentSearchResponse.StudentSearchItem;
import com.jglearning.jg4seasons.admin.web.dto.StudentUpdateRequest;
import com.jglearning.jg4seasons.auth.api.AccountStatus;
import com.jglearning.jg4seasons.auth.api.IssuedCode;
import com.jglearning.jg4seasons.auth.api.NewStudent;
import com.jglearning.jg4seasons.auth.api.StaffAccounts;
import com.jglearning.jg4seasons.auth.api.StudentAccount;
import com.jglearning.jg4seasons.auth.api.StudentAccounts;
import com.jglearning.jg4seasons.auth.api.StudentChange;
import com.jglearning.jg4seasons.auth.api.StudentChangeResult;
import com.jglearning.jg4seasons.clazz.api.ClassRef;
import com.jglearning.jg4seasons.clazz.api.ClassRoster;
import com.jglearning.jg4seasons.clazz.api.MoveResult;
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
import java.util.Optional;
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

    /**
     * 반이 없으면 404, active가 아니면 409 CLASS_NOT_ACTIVE, 사용 기간 밖 403 LICENSE_INACTIVE·학생 수 초과 409 LICENSE_SEATS_FULL(ADR 0031) — 모두
     * 아무도 만들지 않는다(트랜잭션 롤백). 학원 잠금이 동시 등록을 줄 세워 학생 수 한도를 지킨다
     */
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

    /**
     * 빠진 칸은 그대로, 학교·학년 ""은 지움. 이번에 퇴원(active → inactive)이면 그 학원 반 소속을 모두 끝내고(결정 G) 감사 로그 — 다시 inactive를
     * 보내면 아무것도 늘지 않는다. 되살리기(active)는 상태만(반은 이동으로 — 결정 L). 다른 학원 학생·교사 id는 404
     */
    public StudentResponse update(long academyId, long actorId, long studentId, StudentUpdateRequest request) {
        academies.lockForAdminWrite(academyId);
        staff.requireActiveAdmin(academyId, actorId);
        StudentChangeResult result = students.change(
                academyId,
                studentId,
                new StudentChange(
                        Optional.ofNullable(request.name()),
                        Optional.ofNullable(request.school()),
                        Optional.ofNullable(request.grade()),
                        Optional.ofNullable(request.status()).map(AccountStatus::fromValue)));
        if (result.deactivated()) {
            List<Long> left = roster.leaveAll(academyId, studentId, today(academyId));
            auditLogger.record(
                    academyId,
                    actorId,
                    AuditAction.STUDENT_DEACTIVATE,
                    AuditTarget.STUDENT,
                    studentId,
                    Map.of("leftClassIds", left));
        }
        return toResponse(result.account());
    }

    /**
     * 지금 소속을 모두 끝내고 새 반 하나로(결정 H). 퇴원생은 409 STUDENT_INACTIVE, 대상 반이 active가 아니면 409 CLASS_NOT_ACTIVE, 이미 그 반에만
     * 있으면 409 ALREADY_IN_CLASS. 다른 학원 학생·반은 404
     */
    public StudentResponse move(long academyId, long actorId, long studentId, StudentMoveRequest request) {
        academies.lockForAdminWrite(academyId);
        staff.requireActiveAdmin(academyId, actorId);
        StudentAccount account = students.find(academyId, studentId);
        if (account.status() != AccountStatus.ACTIVE) {
            throw new ApiException(AdminErrorCode.STUDENT_INACTIVE, "퇴원한 학생은 먼저 되살려 주세요");
        }
        MoveResult moved = roster.move(academyId, studentId, request.toClassId(), today(academyId));
        auditLogger.record(
                academyId,
                actorId,
                AuditAction.STUDENT_MOVE,
                AuditTarget.STUDENT,
                studentId,
                Map.of("fromClassIds", moved.fromClassIds(), "toClassId", moved.toClassId()));
        return toResponse(account);
    }

    /** 학원 전체(퇴원 포함) 이름 부분 일치, 이름 순, 최대 50명(결정 E). class = 지금 반(여럿이면 id가 가장 작은 반), 없으면 null */
    @Transactional(readOnly = true)
    public StudentSearchResponse search(long academyId, long actorId, StudentSearchRequest request) {
        staff.requireActiveAdmin(academyId, actorId);
        List<StudentAccount> found = students.searchByName(academyId, request.name());
        Map<Long, ClassRef> classes = roster.currentClassOf(
                academyId, found.stream().map(StudentAccount::id).toList());
        return new StudentSearchResponse(found.stream()
                .map(account -> {
                    ClassRef clazz = classes.get(account.id());
                    return new StudentSearchItem(
                            account.id(),
                            account.name(),
                            account.school(),
                            account.grade(),
                            account.status().value(),
                            clazz == null ? null : new StudentSearchClass(clazz.id(), clazz.name()));
                })
                .toList());
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
