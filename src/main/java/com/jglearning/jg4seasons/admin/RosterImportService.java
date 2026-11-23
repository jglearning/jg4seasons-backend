package com.jglearning.jg4seasons.admin;

import com.jglearning.jg4seasons.academy.api.AcademyDirectory;
import com.jglearning.jg4seasons.academy.api.BookLicenses;
import com.jglearning.jg4seasons.admin.RosterImportRules.Draft;
import com.jglearning.jg4seasons.admin.RosterImportRules.Student;
import com.jglearning.jg4seasons.admin.web.dto.ClassCreateRequest;
import com.jglearning.jg4seasons.admin.web.dto.ClassResponse;
import com.jglearning.jg4seasons.admin.web.dto.PersonalCodeItem;
import com.jglearning.jg4seasons.admin.web.dto.RosterImportCheckResponse;
import com.jglearning.jg4seasons.admin.web.dto.RosterImportRequest;
import com.jglearning.jg4seasons.admin.web.dto.RosterImportResponse;
import com.jglearning.jg4seasons.auth.api.AccountStatus;
import com.jglearning.jg4seasons.auth.api.IssuedCode;
import com.jglearning.jg4seasons.auth.api.NewStudent;
import com.jglearning.jg4seasons.auth.api.StaffAccount;
import com.jglearning.jg4seasons.auth.api.StaffAccounts;
import com.jglearning.jg4seasons.auth.api.StudentAccount;
import com.jglearning.jg4seasons.auth.api.StudentAccounts;
import com.jglearning.jg4seasons.clazz.api.ClassDetail;
import com.jglearning.jg4seasons.clazz.api.ClassManagement;
import com.jglearning.jg4seasons.clazz.api.ClassRef;
import com.jglearning.jg4seasons.clazz.api.ClassRoster;
import com.jglearning.jg4seasons.clazz.api.ClassStatus;
import com.jglearning.jg4seasons.clazz.api.ClassTeachers;
import com.jglearning.jg4seasons.clazz.api.LicenseUsage;
import com.jglearning.jg4seasons.clazz.api.LicenseUsages;
import com.jglearning.jg4seasons.clazz.api.NewClass;
import com.jglearning.jg4seasons.content.api.BookDirectory;
import com.jglearning.jg4seasons.content.api.BookLessons;
import com.jglearning.jg4seasons.content.api.BookSummary;
import com.jglearning.jg4seasons.infra.audit.AuditAction;
import com.jglearning.jg4seasons.infra.audit.AuditLogger;
import com.jglearning.jg4seasons.infra.audit.AuditTarget;
import com.jglearning.jg4seasons.infra.web.error.ApiException;
import com.jglearning.jg4seasons.infra.web.error.CommonErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 5.5 명단 가져오기(ADR 0036). 검사는 요청 안 규칙(RosterImportRules)과 데이터 검사(권·교사·기존 반·사용권·같은 이름)를 모아 요청 순서로 돌려준다. 다른
 * 학원의 반·교사는 없는 것과 같다(이름을 문구에 싣지 않음). 사용권은 권별로 가져오기 전체를 합쳐 본다 — 새 학생만 만들므로 (지금 학생 ∪ 들어올 학생) = 지금 +
 * 들어올 수.
 */
@Service
@Transactional
public class RosterImportService {

    static final String WEEKDAYS_INVALID = "수업 요일은 1(월)~7(일)에서 겹치지 않게 하나 이상 골라 주세요";

    private final AcademyDirectory academies;
    private final StaffAccounts staff;
    private final StudentAccounts students;
    private final ClassManagement classes;
    private final ClassRoster roster;
    private final BookDirectory books;
    private final BookLicenses licenses;
    private final LicenseUsages usages;
    private final ClassTeachers classTeachers;
    private final AuditLogger auditLogger;
    private final Clock clock;

    RosterImportService(
            AcademyDirectory academies,
            StaffAccounts staff,
            StudentAccounts students,
            ClassManagement classes,
            ClassRoster roster,
            BookDirectory books,
            BookLicenses licenses,
            LicenseUsages usages,
            ClassTeachers classTeachers,
            AuditLogger auditLogger,
            Clock clock) {
        this.academies = academies;
        this.staff = staff;
        this.students = students;
        this.classes = classes;
        this.roster = roster;
        this.books = books;
        this.licenses = licenses;
        this.usages = usages;
        this.classTeachers = classTeachers;
        this.auditLogger = auditLogger;
        this.clock = clock;
    }

    /**
     * 만들기. 학원 관리 쓰기 잠금 → 검사 → 반 항목 순서대로 (새 반이면 반·진도표·가입 코드 + 담당 교사) → 새 학생·개인 코드 → 소속, 한 트랜잭션. 검사 오류가
     * 있으면 아무것도 남기지 않는다: 첫 오류가 공통 code(VALIDATION·NOT_FOUND)면 여기서 던지고, 반 모듈 code(CLASS_NOT_ACTIVE·LICENSE_*)면 같은
     * 잠금 안에서 같은 데이터를 보는 반 생성·소속 경로가 그 항목에서 그대로 던진다(앞서 만든 것은 롤백). 감사 로그 한 줄(이름·코드 없음)
     */
    public RosterImportResponse create(long academyId, long actorId, RosterImportRequest request) {
        academies.lockForAdminWrite(academyId);
        staff.requireActiveAdmin(academyId, actorId);
        LocalDate today = today(academyId);
        Inspection inspection = inspect(academyId, request, today);
        Optional<RosterIssue> firstError = inspection.errors().stream().findFirst();
        firstError.ifPresent(RosterImportService::throwIfCommon);

        List<RosterImportResponse.ImportedClass> imported = new ArrayList<>();
        List<Long> createdClassIds = new ArrayList<>();
        List<Long> existingClassIds = new ArrayList<>();
        int createdStudents = 0;
        for (int i = 0; i < request.classes().size(); i++) {
            RosterImportRequest.Item item = request.classes().get(i);
            Resolved resolved = inspection.resolved().get(i);
            ClassDetail target;
            boolean created = item.newClass() != null;
            if (created) {
                ClassCreateRequest newClass = item.newClass();
                target = classes.create(
                        academyId,
                        new NewClass(
                                newClass.name(),
                                Objects.requireNonNull(resolved.lessons()),
                                newClass.startDate(),
                                newClass.weekdays()));
                classTeachers.replaceTeachersOf(academyId, target.id(), new TreeSet<>(newClass.teacherIds()));
                createdClassIds.add(target.id());
            } else {
                target = Objects.requireNonNull(resolved.existing());
                existingClassIds.add(target.id());
            }
            List<IssuedCode> issued = students.create(
                    academyId,
                    item.students().stream()
                            .map(s -> new NewStudent(s.name(), s.school(), s.grade()))
                            .toList());
            roster.enroll(
                    academyId,
                    target.id(),
                    issued.stream().map(IssuedCode::studentId).toList(),
                    today);
            createdStudents += issued.size();
            imported.add(toImported(target, created, issued));
        }
        if (firstError.isPresent()) {
            // 검사가 막은 요청이 끝까지 왔다 — 반 모듈 규칙과 검사가 어긋남. 500으로 롤백(입력값 없이)
            throw new IllegalStateException("명단 가져오기 검사 오류가 생성에서 막히지 않음: "
                    + firstError.get().code() + " " + firstError.get().field());
        }
        auditLogger.record(
                academyId,
                actorId,
                AuditAction.ROSTER_IMPORT,
                AuditTarget.ACADEMY,
                academyId,
                Map.of(
                        "createdClassIds", createdClassIds,
                        "existingClassIds", existingClassIds,
                        "createdStudents", createdStudents));
        return new RosterImportResponse(List.copyOf(imported), createdClassIds.size(), createdStudents);
    }

    /** 만들기 실패 문구. 검사 문구에는 이름·반 이름이 들어 있어 Problem Details(detail·errors[].message)에 싣지 않는다(§9.4) — 화면은 검사를 다시 부른다 */
    static final String RECHECK = "명단을 다시 검사해 주세요";

    private static void throwIfCommon(RosterIssue issue) {
        switch (issue.code()) {
            case RosterIssue.VALIDATION -> throw ApiException.validation(issue.field(), RECHECK);
            case RosterIssue.NOT_FOUND -> throw new ApiException(CommonErrorCode.NOT_FOUND, issue.message());
            default -> {
                // 반 모듈 code — 생성 경로가 던진다
            }
        }
    }

    private RosterImportResponse.ImportedClass toImported(
            ClassDetail target, boolean created, List<IssuedCode> issued) {
        BookSummary book = books.summaryOf(target.bookId());
        return new RosterImportResponse.ImportedClass(
                target.id(),
                created,
                target.name(),
                target.accessCode(),
                book.levelCode(),
                new ClassResponse.ClassLevel(book.levelCode(), book.levelNameKo(), book.levelColorHex()),
                book.season(),
                issued.stream()
                        .map(code -> new PersonalCodeItem(code.studentId(), code.name(), code.personalCode()))
                        .toList());
    }

    @Transactional(readOnly = true)
    public RosterImportCheckResponse check(long academyId, long actorId, RosterImportRequest request) {
        staff.requireActiveAdmin(academyId, actorId);
        Inspection inspection = inspect(academyId, request, today(academyId));
        return new RosterImportCheckResponse(
                inspection.errors().stream()
                        .map(e -> new RosterImportCheckResponse.Item(e.field(), e.code(), e.message()))
                        .toList(),
                inspection.warnings().stream()
                        .map(w -> new RosterImportCheckResponse.Item(
                                w.field(), w.code().name(), w.message()))
                        .toList());
    }

    /** 경고 하나. 문구는 화면에 그대로 */
    record WarningItem(String field, RosterWarning code, String message) {}

    /** 반 항목마다 찾은 것(만들기가 다시 조회하지 않게). 오류가 있는 항목은 비어 있을 수 있다 */
    record Resolved(
            @Nullable ClassDetail existing, @Nullable BookLessons lessons) {}

    record Inspection(List<RosterIssue> errors, List<WarningItem> warnings, List<Resolved> resolved) {}

    /** 검사. 아무것도 바꾸지 않는다. today = 학원 타임존 날짜(사용권 판정일) */
    Inspection inspect(long academyId, RosterImportRequest request, LocalDate today) {
        Map<Long, ClassDetail> classById = classes.list(academyId, Optional.empty()).stream()
                .collect(Collectors.toMap(ClassDetail::id, Function.identity()));
        Set<Long> activeStaff = staff.list(academyId).stream()
                .filter(account -> account.status() == AccountStatus.ACTIVE)
                .map(StaffAccount::id)
                .collect(Collectors.toSet());

        List<RosterImportRequest.Item> items = request.classes();
        List<Draft> drafts = new ArrayList<>(items.size());
        List<RosterIssue> errors = new ArrayList<>();
        List<Resolved> resolved = new ArrayList<>(items.size());
        Map<Long, List<Integer>> itemsByBook = new LinkedHashMap<>();
        for (int i = 0; i < items.size(); i++) {
            RosterImportRequest.Item item = items.get(i);
            String at = "classes[" + i + "]";
            boolean exactlyOne = (item.existingClassId() == null) != (item.newClass() == null);
            String label = (i + 1) + "번째 반";
            ClassDetail existing = null;
            BookLessons lessons = null;
            Long bookId = null;
            if (exactlyOne && item.existingClassId() != null) {
                existing = classById.get(item.existingClassId());
                if (existing == null) {
                    errors.add(new RosterIssue(at + ".existingClassId", RosterIssue.NOT_FOUND, "없는 반이에요"));
                } else {
                    label = existing.name();
                    if (existing.status() != ClassStatus.ACTIVE) {
                        errors.add(new RosterIssue(at, RosterIssue.CLASS_NOT_ACTIVE, "진행 중인 반이 아니에요"));
                    } else {
                        bookId = existing.bookId();
                    }
                }
            } else if (exactlyOne) {
                ClassCreateRequest newClass = item.newClass();
                String name = clean(newClass.name());
                if (!name.isEmpty()) {
                    label = name;
                }
                if (name.isEmpty() || name.length() > ClassManagement.NAME_MAX) {
                    errors.add(validation(at + ".newClass.name", "반 이름은 1~" + ClassManagement.NAME_MAX + "자예요"));
                }
                lessons = books.findLessons(newClass.bookCode()).orElse(null);
                if (lessons == null) {
                    errors.add(validation(at + ".newClass.bookCode", "없는 권이에요"));
                } else {
                    bookId = lessons.bookId();
                }
                if (!validWeekdays(newClass.weekdays())) {
                    errors.add(validation(at + ".newClass.weekdays", WEEKDAYS_INVALID));
                }
                if (!activeStaff.containsAll(newClass.teacherIds())) {
                    errors.add(new RosterIssue(at + ".newClass.teacherIds", RosterIssue.NOT_FOUND, "없는 교사예요"));
                }
            }
            if (bookId != null) {
                itemsByBook.computeIfAbsent(bookId, id -> new ArrayList<>()).add(i);
            }
            resolved.add(new Resolved(existing, lessons));
            drafts.add(new Draft(
                    label,
                    exactlyOne,
                    item.students().stream()
                            .map(s -> new Student(s.name(), s.school(), s.grade()))
                            .toList()));
        }
        errors.addAll(RosterImportRules.inspect(drafts));
        errors.addAll(licenseIssues(academyId, items, itemsByBook, today));
        // 요청 순서: 전체(classes) 먼저, 그다음 반 항목 순. 같은 항목 안에서는 넣은 순서(데이터 → 요청 규칙 → 사용권) 그대로
        errors.sort(Comparator.comparingInt(issue -> classIndexOf(issue.field())));
        return new Inspection(List.copyOf(errors), warnings(academyId, items, classById), List.copyOf(resolved));
    }

    private List<RosterIssue> licenseIssues(
            long academyId,
            List<RosterImportRequest.Item> items,
            Map<Long, List<Integer>> itemsByBook,
            LocalDate today) {
        List<RosterIssue> issues = new ArrayList<>();
        Map<Long, Integer> used = null;
        for (Map.Entry<Long, List<Integer>> book : itemsByBook.entrySet()) {
            long bookId = book.getKey();
            if (!licenses.statusOf(academyId, bookId, today).allowsPreparation()) {
                for (int i : book.getValue()) {
                    issues.add(new RosterIssue("classes[" + i + "]", RosterIssue.LICENSE_INACTIVE, "사용 기간이 아니에요"));
                }
                continue;
            }
            OptionalInt limit = licenses.seatLimit(academyId, bookId);
            if (limit.isEmpty()) {
                continue;
            }
            if (used == null) {
                used = usages.of(academyId, today).stream()
                        .collect(Collectors.toMap(u -> u.row().bookId(), LicenseUsage::usedSeats));
            }
            int seats = used.getOrDefault(bookId, 0);
            for (int i : book.getValue()) {
                seats += items.get(i).students().size();
                if (seats > limit.getAsInt()) {
                    issues.add(new RosterIssue(
                            "classes[" + i + "]", RosterIssue.LICENSE_SEATS_FULL, "사용할 수 있는 학생 수를 넘었어요"));
                    break;
                }
            }
        }
        return issues;
    }

    private List<WarningItem> warnings(
            long academyId, List<RosterImportRequest.Item> items, Map<Long, ClassDetail> classById) {
        Set<String> activeClassNames = classById.values().stream()
                .filter(detail -> detail.status() == ClassStatus.ACTIVE)
                .map(detail -> clean(detail.name()))
                .collect(Collectors.toSet());
        Set<String> names = new HashSet<>();
        items.forEach(item -> item.students().forEach(s -> names.add(clean(s.name()))));
        // 학생 표 정렬(utf8mb4_0900_ai_ci)이 영문 대소문자를 구분하지 않아 조회도 그렇게 찾는다 — 묶는 키도 같은 기준
        Map<String, List<StudentAccount>> sameName = students.findActiveByNames(academyId, names).stream()
                .collect(Collectors.groupingBy(account -> nameKey(account.name())));
        Map<Long, ClassRef> classOf = roster.currentClassOf(
                academyId,
                sameName.values().stream()
                        .flatMap(List::stream)
                        .map(StudentAccount::id)
                        .toList());

        List<WarningItem> warnings = new ArrayList<>();
        Set<String> newClassNames = new HashSet<>();
        for (int i = 0; i < items.size(); i++) {
            RosterImportRequest.Item item = items.get(i);
            if (item.newClass() != null) {
                String className = clean(item.newClass().name());
                String at = "classes[" + i + "].newClass.name";
                if (activeClassNames.contains(className)) {
                    warnings.add(new WarningItem(at, RosterWarning.SAME_NAME_CLASS, "같은 이름의 반이 이미 있어요"));
                } else if (!newClassNames.add(className)) {
                    warnings.add(new WarningItem(at, RosterWarning.SAME_NAME_CLASS, "이 명단에 같은 이름의 반이 또 있어요"));
                }
            }
            for (int j = 0; j < item.students().size(); j++) {
                List<StudentAccount> found =
                        sameName.get(nameKey(item.students().get(j).name()));
                if (found == null) {
                    continue;
                }
                String where = found.stream()
                        .map(account -> Optional.ofNullable(classOf.get(account.id()))
                                .map(ClassRef::name)
                                .orElse("반 없음"))
                        .distinct()
                        .collect(Collectors.joining(", "));
                warnings.add(new WarningItem(
                        "classes[" + i + "].students[" + j + "].name",
                        RosterWarning.SAME_NAME_STUDENT,
                        "같은 이름의 학생이 이미 있어요(" + where + ")"));
            }
        }
        return List.copyOf(warnings);
    }

    /** `classes` → -1, `classes[3]…` → 3 */
    static int classIndexOf(String field) {
        if (!field.startsWith("classes[")) {
            return -1;
        }
        return Integer.parseInt(field.substring("classes[".length(), field.indexOf(']')));
    }

    /** 반 만들기(clazz.Weekdays)와 같은 규칙: 하나 이상, 1~7, 겹침 없음 */
    private static boolean validWeekdays(List<Integer> weekdays) {
        Set<Integer> seen = new HashSet<>();
        return !weekdays.isEmpty()
                && weekdays.stream().allMatch(day -> day != null && day >= 1 && day <= 7 && seen.add(day));
    }

    private static RosterIssue validation(String field, String message) {
        return new RosterIssue(field, RosterIssue.VALIDATION, message);
    }

    /** 같은 이름 경고 키: 앞뒤 공백 제거 + 영문 소문자(학생 표 정렬과 같은 대소문자 무시) */
    private static String nameKey(@Nullable String value) {
        return clean(value).toLowerCase(Locale.ROOT);
    }

    private static String clean(@Nullable String value) {
        return Objects.requireNonNullElse(value, "").strip();
    }

    private LocalDate today(long academyId) {
        return LocalDate.now(clock.withZone(academies.zoneOf(academyId)));
    }
}
