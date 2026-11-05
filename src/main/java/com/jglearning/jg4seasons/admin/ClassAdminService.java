package com.jglearning.jg4seasons.admin;

import com.jglearning.jg4seasons.academy.api.AcademyDirectory;
import com.jglearning.jg4seasons.admin.web.dto.AccessCodeResponse;
import com.jglearning.jg4seasons.admin.web.dto.ClassCreateRequest;
import com.jglearning.jg4seasons.admin.web.dto.ClassListResponse;
import com.jglearning.jg4seasons.admin.web.dto.ClassResponse;
import com.jglearning.jg4seasons.admin.web.dto.ClassResponse.ClassTeacher;
import com.jglearning.jg4seasons.admin.web.dto.ClassUpdateRequest;
import com.jglearning.jg4seasons.auth.api.AccountStatus;
import com.jglearning.jg4seasons.auth.api.StaffAccount;
import com.jglearning.jg4seasons.auth.api.StaffAccounts;
import com.jglearning.jg4seasons.clazz.api.ClassChange;
import com.jglearning.jg4seasons.clazz.api.ClassDetail;
import com.jglearning.jg4seasons.clazz.api.ClassManagement;
import com.jglearning.jg4seasons.clazz.api.ClassStatus;
import com.jglearning.jg4seasons.clazz.api.ClassTeachers;
import com.jglearning.jg4seasons.clazz.api.NewClass;
import com.jglearning.jg4seasons.content.api.BookDirectory;
import com.jglearning.jg4seasons.content.api.BookLessons;
import com.jglearning.jg4seasons.content.api.BookSummary;
import com.jglearning.jg4seasons.infra.audit.AuditAction;
import com.jglearning.jg4seasons.infra.audit.AuditLogger;
import com.jglearning.jg4seasons.infra.web.error.ApiException;
import com.jglearning.jg4seasons.infra.web.error.CommonErrorCode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 5.1 관리자 반 API(ADM-01·ADM-03·AUTH-06). 반·진도표·코드는 clazz, 권 → 회차는 content, 교사 확인은 auth, 감사는 infra.audit — 한 트랜잭션에서
 * 조립한다(교사 확인이 404면 반도 진도표도 만들지 않음). academyId·actorId는 컨트롤러가 인증 주체(JWT)에서 넘긴다. 모든 메서드는 먼저 요청한 사람이 지금도
 * 활성 관리자인지 확인한다(JWT role만 믿지 않음). 쓰기는 그보다 먼저 학원 잠금(AcademyDirectory.lockForAdminWrite)으로 같은 학원 관리 쓰기를 줄
 * 세운다 — 담당 교사 배정은 교사 행·반 행·class_teacher(FK로 user 행)를 경로마다 다른 순서로 잠가 교착이 났다. 교사 활성 확인도 잠금 뒤라 경쟁이 없다.
 */
@Service
@Transactional
public class ClassAdminService {

    private final AcademyDirectory academies;
    private final StaffAccounts accounts;
    private final ClassManagement classes;
    private final ClassTeachers classTeachers;
    private final BookDirectory books;
    private final AuditLogger auditLogger;

    ClassAdminService(
            AcademyDirectory academies,
            StaffAccounts accounts,
            ClassManagement classes,
            ClassTeachers classTeachers,
            BookDirectory books,
            AuditLogger auditLogger) {
        this.academies = academies;
        this.accounts = accounts;
        this.classes = classes;
        this.classTeachers = classTeachers;
        this.books = books;
        this.auditLogger = auditLogger;
    }

    @Transactional(readOnly = true)
    public ClassListResponse list(long academyId, long actorId, Optional<String> status) {
        accounts.requireActiveAdmin(academyId, actorId);
        Map<Long, String> staffNames = staffNames(academyId);
        List<ClassResponse> items = classes.list(academyId, status.map(ClassStatus::fromValue)).stream()
                .map(detail -> toResponse(academyId, detail, staffNames))
                .toList();
        return new ClassListResponse(items, items.size());
    }

    @Transactional(readOnly = true)
    public ClassResponse find(long academyId, long actorId, long classId) {
        accounts.requireActiveAdmin(academyId, actorId);
        return toResponse(academyId, classes.find(academyId, classId), staffNames(academyId));
    }

    public ClassResponse create(long academyId, long actorId, ClassCreateRequest request) {
        academies.lockForAdminWrite(academyId);
        accounts.requireActiveAdmin(academyId, actorId);
        BookLessons lessons =
                books.findLessons(request.bookCode()).orElseThrow(() -> ApiException.validation("bookCode", "없는 권이에요"));
        Set<Long> teacherIds = requireActiveStaff(academyId, request.teacherIds());
        ClassDetail created = classes.create(
                academyId,
                new NewClass(
                        request.name(),
                        lessons.bookId(),
                        request.startDate(),
                        request.weekdays(),
                        lessons.lessonIdsByNumber()));
        classTeachers.replaceTeachersOf(academyId, created.id(), teacherIds);
        return toResponse(academyId, created, staffNames(academyId));
    }

    public ClassResponse update(long academyId, long actorId, long classId, ClassUpdateRequest request) {
        academies.lockForAdminWrite(academyId);
        accounts.requireActiveAdmin(academyId, actorId);
        Optional<Set<Long>> teacherIds =
                Optional.ofNullable(request.teacherIds()).map(ids -> requireActiveStaff(academyId, ids));
        ClassDetail changed = classes.change(
                academyId,
                classId,
                new ClassChange(
                        Optional.ofNullable(request.name()),
                        Optional.ofNullable(request.status()).map(ClassStatus::fromValue),
                        Optional.ofNullable(request.allowFutureLessons())));
        teacherIds.ifPresent(ids -> classTeachers.replaceTeachersOf(academyId, classId, ids));
        return toResponse(academyId, changed, staffNames(academyId));
    }

    /** 이미 입장한 학생의 로그인은 그대로. 감사 detail에는 코드를 남기지 않는다 */
    public AccessCodeResponse regenerateAccessCode(long academyId, long actorId, long classId) {
        academies.lockForAdminWrite(academyId);
        accounts.requireActiveAdmin(academyId, actorId);
        ClassDetail reissued = classes.regenerateAccessCode(academyId, classId);
        auditLogger.record(academyId, actorId, AuditAction.CODE_REGENERATE, AuditLogger.TARGET_CLASS, classId, null);
        return new AccessCodeResponse(reissued.accessCode(), reissued.codeIssuedAt());
    }

    /** 그 학원의 활성 교사·관리자만. 다른 학원·학생·없는 id·비활성은 404(존재를 드러내지 않음) */
    private Set<Long> requireActiveStaff(long academyId, List<Long> ids) {
        Set<Long> unique = new TreeSet<>(ids);
        for (Long id : unique) {
            StaffAccount account = accounts.find(academyId, id);
            if (account.status() != AccountStatus.ACTIVE) {
                throw new ApiException(CommonErrorCode.NOT_FOUND, "교사를 찾을 수 없어요");
            }
        }
        return unique;
    }

    private Map<Long, String> staffNames(long academyId) {
        return accounts.list(academyId).stream().collect(Collectors.toMap(StaffAccount::id, StaffAccount::name));
    }

    private ClassResponse toResponse(long academyId, ClassDetail detail, Map<Long, String> staffNames) {
        BookSummary book = books.summaryOf(detail.bookId());
        List<ClassTeacher> teachers = classTeachers.teacherIdsOf(academyId, detail.id()).stream()
                .map(id -> new ClassTeacher(id, nameOf(staffNames, id)))
                .toList();
        return new ClassResponse(
                detail.id(),
                detail.name(),
                book.bookCode(),
                book.levelCode(),
                book.season(),
                detail.startDate(),
                detail.weekdays(),
                detail.accessCode(),
                detail.codeIssuedAt(),
                detail.allowFutureLessons(),
                detail.status().value(),
                teachers,
                detail.studentCount());
    }

    private static String nameOf(Map<Long, String> staffNames, long id) {
        String name = staffNames.get(id);
        if (name == null) {
            // 담당 교사는 그 학원 교사·관리자로만 넣는다 — 없으면 데이터 불변식 위반
            throw new IllegalStateException("담당 교사가 그 학원 교사·관리자가 아니다: " + id);
        }
        return name;
    }
}
